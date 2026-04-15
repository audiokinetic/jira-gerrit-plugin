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

import com.atlassian.jira.issue.Issue;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface IssueReviewsManager {

    /**
     * Gets all Gerrit reviews related to the {@link Issue#getKey() specified issue key and all previus keys associated with the issue}.
     *
     * @param issue JIRA issue
     * @return A set of unique issue keys, including actual one
     */
    Set<String> getIssueKeys(Issue issue);

    /**
     * Gets all Gerrit reviews related to the {@link Issue#getKey() specific issue key}.
     *
     * @param issue the JIRA issue
     * @return A list of {@link GerritChange}s, as retrieved from Gerrit.
     * @throws GerritQueryException If any failure occurs while querying the Gerrit server.
     */
    List<GerritChange> getReviewsForIssue(Issue issue) throws GerritQueryException;

    /**
     * Performs approvals/reviews of all changes using the system-level Gerrit credentials.
     *
     * @param issue the JIRA issue
     * @param changes the set of Gerrit changes
     * @param args arguments to add to each approval (e.g. {@code "--verified +1 --submit"})
     * @return whether all approvals were successful
     * @throws IOException if an SSH communication error occurs
     */
    boolean doApprovals(Issue issue, List<GerritChange> changes, String args) throws IOException;
}
