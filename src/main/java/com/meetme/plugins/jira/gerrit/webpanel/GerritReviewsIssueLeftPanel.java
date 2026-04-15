/*
 * Copyright 2012 MeetMe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.meetme.plugins.jira.gerrit.webpanel;

import com.meetme.plugins.jira.gerrit.SessionKeys;
import com.meetme.plugins.jira.gerrit.data.GerritConfiguration;
import com.meetme.plugins.jira.gerrit.data.IssueReviewsManager;
import com.meetme.plugins.jira.gerrit.data.dto.GerritChange;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.webfragment.CacheableContextProvider;
import com.atlassian.jira.plugin.webfragment.JiraWebContext;
import com.atlassian.jira.plugin.webfragment.model.JiraHelper;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.plugin.PluginParseException;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;

import jakarta.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

/**
 * Context provider for the Gerrit Reviews left-side panel on the View Issue page.
 *
 * <p><strong>Thread safety:</strong> this class is registered as an OSGi singleton component.
 * All per-request state (filter selections) is derived from the incoming request / HTTP session on
 * every invocation — no mutable instance fields are used.
 */
public class GerritReviewsIssueLeftPanel implements CacheableContextProvider {
    private static final Logger log = LoggerFactory.getLogger(GerritReviewsIssueLeftPanel.class);

    private static final String KEY_ISSUE = "issue";
    private static final String KEY_CHANGES = "changes";
    private static final String KEY_ERROR = "error";

    private final IssueReviewsManager reviewsManager;
    private final GerritConfiguration config;

    public GerritReviewsIssueLeftPanel(IssueReviewsManager reviewsManager, GerritConfiguration config) {
        super();
        this.reviewsManager = reviewsManager;
        this.config = config;
    }

    @Override
    public void init(Map<String, String> params) throws PluginParseException {
        // No init
    }

    @Override
    public Map<String, Object> getContextMap(Map<String, Object> context) {
        final Issue issue = (Issue) context.get(KEY_ISSUE);
        final MapBuilder<String, Object> paramsBuilder = MapBuilder.newBuilder(context);

        // Resolve filter params per-request — no mutable instance state
        final String gerritIssueType = resolveFilterParam(context, "gerritIssueType",
                SessionKeys.VIEWISSUE_REVIEWS_ISSUETYPE, IssueTypeOptionsProvider.DEFAULT_ISSUE_TYPE);
        final String gerritIssueStatus = resolveFilterParam(context, "gerritIssueStatus",
                SessionKeys.VIEWISSUE_REVIEWS_ISSUESTATUS, IssueStatusOptionsProvider.DEFAULT_STATUS);
        final String gerritReviewStatus = resolveFilterParam(context, "gerritReviewStatus",
                SessionKeys.VIEWISSUE_REVIEWS_REVIEWSTATUS, ReviewStatusOptionsProvider.DEFAULT_STATUS);

        log.debug("issuetype={}, issuestatus={}, reviewstatus={}", gerritIssueType, gerritIssueStatus, gerritReviewStatus);

        paramsBuilder.add("gerritIssueType", gerritIssueType);

        // Gerrit 2.5 introduces Dashboards. Provide an easy-to-access Dashboard fragment
        URI baseUri = this.config.getHttpBaseUrl();
        if (baseUri != null) {
            String baseUrl = baseUri.toASCIIString();
            if (!StringUtils.isBlank(baseUrl)) {
                String searchQuery = String.format(this.config.getIssueSearchQuery(), issue.getKey());
                String part = String.format("&For+%s=%s", issue.getKey(), searchQuery);
                paramsBuilder.add("dashboardUrl", baseUrl + "#/dashboard/?title=From+JIRA" + part);
                paramsBuilder.add("dashboardPart", part);
                paramsBuilder.add("dashboardKey", issue.getKey());
            }
        }

        List<GerritChange> changes = new ArrayList<>();

        try {
            if (IssueTypeOptionsProvider.wantsIssue(gerritIssueType)
                    && (IssueStatusOptionsProvider.wantsUnresolved(gerritIssueStatus) || IssueStatusOptionsProvider.isIssueOpen(issue))) {
                changes.addAll(reviewsManager.getReviewsForIssue(issue));
            }

            if (IssueTypeOptionsProvider.wantsSubtasks(gerritIssueType)) {
                addSubtaskChanges(changes, issue, gerritIssueStatus);
            }

            if (!ReviewStatusOptionsProvider.wantsClosedReviews(gerritReviewStatus)) {
                changes.removeIf(change -> !change.isOpen());
            }

            Collections.sort(changes);
            paramsBuilder.add(KEY_CHANGES, changes);
        } catch (GerritQueryException e) {
            paramsBuilder.add(KEY_ERROR, e.getMessage());
        }

        log.debug("Showing changes: {}", changes);

        return paramsBuilder.toMap();
    }

    /** Resolves a filter parameter: request param → session → default. Persists result in session. */
    private static String resolveFilterParam(Map<String, Object> context,
            String paramName, String sessionKey, String defaultValue) {
        final JiraHelper jiraHelper = (JiraHelper) context.get(JiraWebContext.CONTEXT_KEY_HELPER);
        final HttpSession session = jiraHelper.getRequest().getSession();

        String value = jiraHelper.getRequest().getParameter(paramName);
        if (StringUtils.isBlank(value)) {
            value = (String) session.getAttribute(sessionKey);
        }
        if (StringUtils.isBlank(value)) {
            value = defaultValue;
        }
        session.setAttribute(sessionKey, value);
        return value;
    }

    @Override
    public String getUniqueContextKey(Map<String, Object> context) {
        final Issue issue = (Issue) context.get(KEY_ISSUE);
        final String issueType = resolveFilterParam(context, "gerritIssueType",
                SessionKeys.VIEWISSUE_REVIEWS_ISSUETYPE, IssueTypeOptionsProvider.DEFAULT_ISSUE_TYPE);
        final String issueStatus = resolveFilterParam(context, "gerritIssueStatus",
                SessionKeys.VIEWISSUE_REVIEWS_ISSUESTATUS, IssueStatusOptionsProvider.DEFAULT_STATUS);
        final String reviewStatus = resolveFilterParam(context, "gerritReviewStatus",
                SessionKeys.VIEWISSUE_REVIEWS_REVIEWSTATUS, ReviewStatusOptionsProvider.DEFAULT_STATUS);
        return String.format("issueReviews:%d:%s:%s:%s", issue.getId(), issueStatus, issueType, reviewStatus);
    }

    private void addSubtaskChanges(final List<GerritChange> changes, final Issue issue,
            final String gerritIssueStatus) throws GerritQueryException {
        log.trace("Adding all changes for subtasks of {}", issue.getKey());
        for (Issue subtask : issue.getSubTaskObjects()) {
            log.trace(" .. checking for {}", subtask.getKey());
            log.debug("wants unresolved: {}, or isopen {}: {}",
                    IssueStatusOptionsProvider.wantsUnresolved(gerritIssueStatus),
                    subtask.getKey(),
                    IssueStatusOptionsProvider.isIssueOpen(subtask));

            if (IssueStatusOptionsProvider.isIssueOpen(subtask) || IssueStatusOptionsProvider.wantsUnresolved(gerritIssueStatus)) {
                log.debug(" .. adding all changes for subtask: {}", subtask.getKey());
                changes.addAll(reviewsManager.getReviewsForIssue(subtask));
            }
        }
        log.trace("... now: {}", changes);
    }
}
