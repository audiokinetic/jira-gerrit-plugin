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
package com.meetme.plugins.jira.gerrit.data;

import com.meetme.plugins.jira.gerrit.data.dto.GerritChange;

import com.atlassian.cache.Cache;
import com.atlassian.cache.CacheException;
import com.atlassian.cache.CacheManager;
import com.atlassian.cache.CacheSettingsBuilder;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class IssueReviewsImpl implements IssueReviewsManager {
    private static final Logger log = LoggerFactory.getLogger(IssueReviewsImpl.class);

    /** Base name for the cache; a version suffix is appended on each reconfiguration. */
    private static final String CACHE_NAME_BASE =
            IssueReviewsManager.class.getName() + ".issueChanges.cache";

    private volatile Cache<String, List<GerritChange>> cache;
    private final GerritConfiguration configuration;
    private final IssueManager jiraIssueManager;
    private final CacheManager cacheManager;
    private final IssueReviewsCacheLoader cacheLoader;

    /** Incremented on each reconfiguration to force a new cache name (and thus new settings). */
    private volatile int cacheVersion = 0;

    public IssueReviewsImpl(
            GerritConfiguration configuration,
            IssueManager jiraIssueManager,
            CacheManager cacheManager,
            IssueReviewsCacheLoader cacheLoader
    ) {
        this.configuration = configuration;
        this.jiraIssueManager = jiraIssueManager;
        this.cacheManager = cacheManager;
        this.cacheLoader = cacheLoader;
        this.cache = buildCache(configuration.getCacheMaxEntries(), configuration.getCacheExpireMinutes());
        if (configuration.isCacheEnabled()) {
            log.info("Review cache initialised: maxEntries={}, expireMinutes={}",
                    configuration.getCacheMaxEntries(), configuration.getCacheExpireMinutes());
        } else {
            log.info("Review cache disabled — requests will go directly to Gerrit.");
        }
    }

    private Cache<String, List<GerritChange>> buildCache(int maxEntries, int expireMinutes) {
        var builder = new CacheSettingsBuilder()
                .flushable()
                .statisticsEnabled()
                .maxEntries(maxEntries)
                .replicateAsynchronously();

        if (configuration.isCacheExpireOnIdle()) {
            builder = builder.expireAfterAccess(expireMinutes, TimeUnit.MINUTES);
        } else {
            builder = builder.expireAfterWrite(expireMinutes, TimeUnit.MINUTES);
        }

        return cacheManager.getCache(
                CACHE_NAME_BASE + ".v" + cacheVersion,
                cacheLoader,
                builder.build()
        );
    }

    @Override
    public Set<String> getIssueKeys(Issue issue) {
        return jiraIssueManager.getAllIssueKeys(issue.getId());
    }

    @Override
    public List<GerritChange> getReviewsForIssue(Issue issue) throws GerritQueryException {
        List<GerritChange> gerritChanges = new ArrayList<>();
        Set<String> allIssueKeys = getIssueKeys(issue);

        if (!configuration.isCacheEnabled()) {
            for (String key : allIssueKeys) {
                try {
                    List<GerritChange> changes = cacheLoader.load(key);
                    if (changes != null) gerritChanges.addAll(changes);
                } catch (CacheException exc) {
                    if (exc.getCause() instanceof GerritQueryException gqe) {
                        throw gqe;
                    }
                    log.error("Error querying Gerrit directly (cache disabled)", exc);
                    throw exc;
                }
            }
        } else {
            for (String key : allIssueKeys) {
                try {
                    List<GerritChange> changes = cache.get(key);
                    if (changes != null) gerritChanges.addAll(changes);
                } catch (CacheException exc) {
                    if (exc.getCause() instanceof GerritQueryException gqe) {
                        throw gqe;
                    }
                    log.error("Error fetching from cache", exc);
                    throw exc;
                }
            }
        }

        return gerritChanges;
    }

    @Override
    public void flushCache() {
        cache.removeAll();
        log.info("Gerrit reviews cache flushed by administrator.");
    }

    @Override
    public synchronized void reconfigureCache() {
        cache.removeAll();
        cacheVersion++; // new name so that CacheManager creates a fresh instance with updated settings
        int maxEntries = configuration.getCacheMaxEntries();
        int expireMinutes = configuration.getCacheExpireMinutes();
        cache = buildCache(maxEntries, expireMinutes);
        if (configuration.isCacheEnabled()) {
            log.info("Review cache reconfigured: maxEntries={}, expireMinutes={}", maxEntries, expireMinutes);
        } else {
            log.info("Review cache reconfigured (disabled — requests go directly to Gerrit).");
        }
    }

    @Override
    public boolean doApprovals(Issue issue, List<GerritChange> changes, String args) throws IOException {
        Set<String> issueKeys = getIssueKeys(issue);
        GerritCommand command = new GerritCommand(configuration);

        boolean result = true;
        for (String issueKey : issueKeys) {
            boolean commandResult = command.doReviews(changes, args);
            result &= commandResult;
            log.debug("doApprovals {}, changes={}, args={}; result={}", issueKey, changes, args, commandResult);
            cache.remove(issueKey);
        }

        return result;
    }
}
