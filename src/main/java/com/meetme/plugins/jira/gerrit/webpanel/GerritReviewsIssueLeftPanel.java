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

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

import webwork.action.ActionContext;

public class GerritReviewsIssueLeftPanel implements CacheableContextProvider {
    private static final Logger log = LoggerFactory.getLogger(GerritReviewsIssueLeftPanel.class);

    private static final String KEY_ISSUE = "issue";
    private static final String KEY_CHANGES = "changes";
    private static final String KEY_ERROR = "error";

    private IssueReviewsManager reviewsManager;

    private String gerritIssueType = null;
    private String gerritReviewStatus = null;
    private String gerritIssueStatus = null;

    private GerritConfiguration config;

    public GerritReviewsIssueLeftPanel(IssueReviewsManager reviewsManager, GerritConfiguration config) {
        super();
        this.reviewsManager = reviewsManager;
        this.config = config;
    }

    public void init(Map<String, String> params) throws PluginParseException {
        // No init
    }

    @Override
    public Map<String, Object> getContextMap(Map<String, Object> context) {
        setUpRequestParams(context);

        final Issue issue = (Issue) context.get(KEY_ISSUE);
        final MapBuilder<String, Object> paramsBuilder = MapBuilder.newBuilder(context);

        // determine which one is checked?
        final String gerritIssueType = getGerritIssueType();
        final String gerritIssueStatus = getGerritIssueStatus();
        final String gerritReviewStatus = getGerritReviewStatus();

        log.debug("issuetype=" + gerritIssueType + ", issuestatus=" + gerritIssueStatus + ", reviewstatus=" + gerritReviewStatus);

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
                addIssueChanges(changes, issue);
            }

            if (IssueTypeOptionsProvider.wantsSubtasks(gerritIssueType)) {
                addSubtaskChanges(changes, issue, gerritIssueStatus);
            }

            if (!ReviewStatusOptionsProvider.wantsClosedReviews(gerritReviewStatus)) {
                removeClosedReviews(changes);
            }

            Collections.sort(changes);
            paramsBuilder.add(KEY_CHANGES, changes);
        } catch (GerritQueryException e) {
            paramsBuilder.add(KEY_ERROR, e.getMessage());
        }

        log.debug("Showing changes: " + changes);

        return paramsBuilder.toMap();
    }

    private static int removeClosedReviews(List<GerritChange> changes) {
        Iterator<GerritChange> it = changes.iterator();
        int c = 0;

        while (it.hasNext()) {
            GerritChange change = it.next();
            if (!change.isOpen()) {
                it.remove();
                c += 1;
            }
        }

        return c;
    }

    private void addSubtaskChanges(final List<GerritChange> changes, final Issue issue, final String gerritIssueStatus) throws GerritQueryException {
        log.trace("Adding all changes for subtasks of " + issue.getKey());
        for (Issue subtask : issue.getSubTaskObjects()) {
            log.trace(" .. checking for " + subtask.getKey());

            log.debug("wants unresolved: " + IssueStatusOptionsProvider.wantsUnresolved(gerritIssueStatus) + ", or isopen " + subtask.getKey() + ": "
                    + IssueStatusOptionsProvider.isIssueOpen(subtask));

            if (IssueStatusOptionsProvider.isIssueOpen(subtask) || IssueStatusOptionsProvider.wantsUnresolved(gerritIssueStatus)) {
                log.debug(" .. adding all changes for subtask: " + subtask.getKey());
                changes.addAll(reviewsManager.getReviewsForIssue(subtask));
            }
        }

        log.trace("... now: " + changes);
    }

    private void addIssueChanges(List<GerritChange> changes, Issue issue) throws GerritQueryException {
        changes.addAll(reviewsManager.getReviewsForIssue(issue));
    }

    @SuppressWarnings("unchecked")
    private void setUpRequestParams(Map<String, Object> context) {
        // This is a little unorthodox... We're being rendered inside the ViewIssue web action,
        // which is where other modules manage their session vars, but we don't have access to that
        // automation the same way.
        // So instead, we have to manually retrieve the request parameter(s), and stuff those into
        // the session ourselves.
        final JiraHelper jiraHelper = (JiraHelper) context.get(JiraWebContext.CONTEXT_KEY_HELPER);
        final String gerritIssueType = jiraHelper.getRequest().getParameter("gerritIssueType");
        final String gerritReviewStatus = jiraHelper.getRequest().getParameter("gerritReviewStatus");
        final String gerritIssueStatus = jiraHelper.getRequest().getParameter("gerritIssueStatus");

        if (gerritIssueType != null && !StringUtils.isBlank(gerritIssueType)) {
            this.gerritIssueType = gerritIssueType;
        }
        ActionContext.getSession().put(SessionKeys.VIEWISSUE_REVIEWS_ISSUETYPE, this.gerritIssueType);

        if (gerritReviewStatus != null && !StringUtils.isBlank(gerritReviewStatus)) {
            this.gerritReviewStatus = gerritReviewStatus;
        }
        ActionContext.getSession().put(SessionKeys.VIEWISSUE_REVIEWS_REVIEWSTATUS, this.gerritReviewStatus);

        if (gerritIssueStatus != null && !StringUtils.isBlank(gerritIssueStatus)) {
            this.gerritIssueStatus = gerritIssueStatus;
        }
        ActionContext.getSession().put(SessionKeys.VIEWISSUE_REVIEWS_ISSUESTATUS, this.gerritIssueStatus);
    }

    @Override
    public String getUniqueContextKey(Map<String, Object> context) {
        final Issue issue = (Issue) context.get(KEY_ISSUE);
        return String.format("issueReviews:%d:%s:%s:%s", issue.getId(), getGerritIssueStatus(), getGerritIssueType(), getGerritReviewStatus());
    }

    public String getGerritIssueType() {
        if (gerritIssueType == null) {
            gerritIssueType = (String) ActionContext.getSession().get(SessionKeys.VIEWISSUE_REVIEWS_ISSUETYPE);
        }

        if (gerritIssueType == null) {
            gerritIssueType = IssueTypeOptionsProvider.DEFAULT_ISSUE_TYPE;
        }

        return gerritIssueType;
    }

    public String getGerritReviewStatus() {
        if (gerritReviewStatus == null) {
            gerritReviewStatus = (String) ActionContext.getSession().get(SessionKeys.VIEWISSUE_REVIEWS_REVIEWSTATUS);
        }

        if (gerritReviewStatus == null) {
            gerritReviewStatus = ReviewStatusOptionsProvider.DEFAULT_STATUS;
        }

        return gerritReviewStatus;
    }

    public String getGerritIssueStatus() {
        if (gerritIssueStatus == null) {
            gerritIssueStatus = (String) ActionContext.getSession().get(SessionKeys.VIEWISSUE_REVIEWS_ISSUESTATUS);
        }

        if (gerritIssueStatus == null) {
            gerritIssueStatus = IssueStatusOptionsProvider.DEFAULT_STATUS;
        }

        return gerritIssueStatus;
    }
}
