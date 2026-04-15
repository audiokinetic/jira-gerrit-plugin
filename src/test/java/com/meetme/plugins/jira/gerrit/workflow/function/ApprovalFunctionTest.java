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
package com.meetme.plugins.jira.gerrit.workflow.function;

import com.meetme.plugins.jira.gerrit.data.GerritConfiguration;
import com.meetme.plugins.jira.gerrit.data.IssueReviewsManager;
import com.meetme.plugins.jira.gerrit.data.dto.GerritChange;
import com.meetme.plugins.jira.gerrit.workflow.AbstractWorkflowTest;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Joe Hansche
 */
public abstract class ApprovalFunctionTest extends AbstractWorkflowTest {
    @Mock
    PropertySet ps;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUpUser();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test method for {@link ApprovalFunction#ApprovalFunction(GerritConfiguration, IssueReviewsManager)}.
     */
    @Test
    public void testCtor() {
        ApprovalFunction obj = new ApprovalFunction(configuration, reviewsManager);
        assertTrue(obj instanceof ApprovalFunction);
    }

    /**
     * Test method for {@link ApprovalFunction#isConfigurationReady()}.
     */
    @Test
    public void testConfigurationReady() {
        ApprovalFunction obj = new ApprovalFunction(null, null);
        // configuration is null
        assertFalse(obj.isConfigurationReady());

        obj = new ApprovalFunction(configuration, null);
        // SSH is valid by default via setUpConfiguration()
        assertTrue(obj.isConfigurationReady());

        // SSH file not exist
        when(configuration.getSshPrivateKey().exists()).thenReturn(false);
        assertFalse(obj.isConfigurationReady());

        // SSH file is null
        when(configuration.getSshPrivateKey()).thenReturn(null);
        assertFalse(obj.isConfigurationReady());

        // Username is null
        when(configuration.getSshUsername()).thenReturn(null);
        assertFalse(obj.isConfigurationReady());

        // Hostname is null
        when(configuration.getSshHostname()).thenReturn(null);
        assertFalse(obj.isConfigurationReady());
    }

    /**
     * Test method for {@link ApprovalFunction#execute(Map, Map, PropertySet)}.
     */
    @Test(expected = IllegalStateException.class)
    public void testExecute_notReady() throws WorkflowException {
        ApprovalFunction obj = new ApprovalFunction(null, null);
        obj.execute(null, null, null);
    }

    @Test
    public void testGetIssueKey() {
        ApprovalFunction obj = new ApprovalFunction(configuration, null);
        String actual = obj.getIssueKey(transientVars);
        assertEquals("FOO-123", actual);
    }

    @Test(expected = WorkflowException.class)
    public void testGetReviews_failure() throws WorkflowException, GerritQueryException {
        stubFailingReviews();
        ApprovalFunction obj = new ApprovalFunction(configuration, reviewsManager);
        obj.getReviews(mockIssue);
    }

    @Test
    public void testGetReviews_success() throws WorkflowException, GerritQueryException {
        stubOneReview();
        ApprovalFunction obj = new ApprovalFunction(configuration, reviewsManager);
        List<GerritChange> actual = obj.getReviews(mockIssue);
        assertEquals(1, actual.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecute_gerritFailed() throws WorkflowException, GerritQueryException, IOException {
        stubOneReview();
        ApprovalFunction obj = new ApprovalFunction(configuration, reviewsManager);
        when(reviewsManager.doApprovals(eq(mockIssue), Mockito.anyList(), Mockito.anyString())).thenReturn(false);
        // doApprovals returning false does NOT throw — implementation logs a warning instead
        obj.execute(transientVars, args, ps);

        verify(reviewsManager, times(1)).doApprovals(eq(mockIssue), anyList(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test(expected = WorkflowException.class)
    public void testExecute_gerritThrows() throws WorkflowException, GerritQueryException, IOException {
        stubOneReview();
        IOException exc = new IOException();
        ApprovalFunction obj = new ApprovalFunction(configuration, reviewsManager);
        when(reviewsManager.doApprovals(eq(mockIssue), Mockito.anyList(), Mockito.anyString())).thenThrow(exc);
        obj.execute(transientVars, args, ps);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecute_success() throws WorkflowException, GerritQueryException, IOException {
        stubOneReview();
        ApprovalFunction obj = new ApprovalFunction(configuration, reviewsManager);
        when(reviewsManager.doApprovals(eq(mockIssue), Mockito.anyList(), Mockito.anyString())).thenReturn(true);
        obj.execute(transientVars, args, ps);

        verify(reviewsManager, times(1)).doApprovals(eq(mockIssue), anyList(), anyString());
    }
}
