package com.meetme.plugins.jira.gerrit.webpanel;

import com.meetme.plugins.jira.gerrit.data.GerritConfiguration;
import com.meetme.plugins.jira.gerrit.data.IssueReviewsManager;
import com.meetme.plugins.jira.gerrit.data.dto.GerritChange;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;

import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ShowReviewsWebPanelConditionTest {

    ShowReviewsWebPanelCondition showReviewsWebPanelCondition;

    @Mock
    private GerritConfiguration gerritConfiguration;

    @Mock
    private Issue issue;

    @Mock
    private IssueReviewsManager issueReviewsManager;

    @Mock
    private ProjectManager projectManager;

    private List<Project> projects;

    @Before
    public void setUp() {
        initMocks(this);
        projects = createMockProjects();
        when(issue.getProjectId()).thenReturn(1L);
        when(issue.getId()).thenReturn(10000000L);
        when(projectManager.getProjects()).thenReturn(projects);
        showReviewsWebPanelCondition = new ShowReviewsWebPanelCondition(issueReviewsManager, gerritConfiguration);
        when(gerritConfiguration.getUseGerritProjectWhitelist()).thenReturn(true);
    }

    @Test
    public void shouldDisplayWithAlwaysFlag() throws GerritQueryException {
        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(true);
        when(issueReviewsManager.getReviewsForIssue(any(Issue.class))).thenReturn(Lists.newArrayList());
        List<String> ids = projects.stream().map(p -> p.getId().toString()).collect(Collectors.toList());
        when(gerritConfiguration.getIdsOfKnownGerritProjects()).thenReturn(ids);

        assertTrue(showReviewsWebPanelCondition.shouldDisplay(singletonMap("issue", issue)));
    }

    @Test
    public void shouldDisplayMapEqualsNull() {
        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(false);

        assertFalse(showReviewsWebPanelCondition.shouldDisplay(null));
    }

    @Test
    public void shouldDisplayMapWithoutIssue() {
        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(false);

        assertFalse(showReviewsWebPanelCondition.shouldDisplay(Maps.newHashMap()));
    }

    @Test
    public void shouldDisplayEmptyWhiteList() {
        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(false);
        when(gerritConfiguration.getIdsOfKnownGerritProjects()).thenReturn(Lists.newArrayList());

        assertFalse(showReviewsWebPanelCondition.shouldDisplay(singletonMap("issue", issue)));
    }

    @Test
    public void shouldDisplayProjectIsOnWhiteList() throws Exception {
        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(false);
        when(issueReviewsManager.getReviewsForIssue(any(Issue.class))).thenReturn(singletonList(new GerritChange()));
        List<String> ids = projects.stream().map(p -> p.getId().toString()).collect(Collectors.toList());
        when(gerritConfiguration.getIdsOfKnownGerritProjects()).thenReturn(ids);
        assertTrue(showReviewsWebPanelCondition.shouldDisplay(singletonMap("issue", issue)));
    }

    @Test
    public void shouldDisplayProjectIsNotOnWhiteList() {
        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(false);
        List<String> ids = projects.stream()
                .filter(project -> !project.getId().equals(1L))
                .map(project -> project.getId().toString())
                .collect(Collectors.toList());
        when(gerritConfiguration.getIdsOfKnownGerritProjects()).thenReturn(ids);
        assertFalse(showReviewsWebPanelCondition.shouldDisplay(singletonMap("issue", issue)));
    }

    @Test
    public void shouldDisplayNoConnectionToGerrit() throws GerritQueryException {
        List<String> ids = projects.stream().map(p -> p.getId().toString()).collect(Collectors.toList());
        when(gerritConfiguration.getIdsOfKnownGerritProjects()).thenReturn(ids);
        when(issueReviewsManager.getReviewsForIssue(any(Issue.class))).thenThrow(new GerritQueryException());

        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(true);
        assertTrue(showReviewsWebPanelCondition.shouldDisplay(singletonMap("issue", issue)));

        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(false);
        assertFalse(showReviewsWebPanelCondition.shouldDisplay(singletonMap("issue", issue)));
    }

    private static List<Project> createMockProjects() {
        List<Project> list = new ArrayList<>();
        for (long i = 0; i < 3; i++) {
            Project p = mock(Project.class);
            final long id = i;
            when(p.getId()).thenReturn(id);
            when(p.getKey()).thenReturn("KEY_" + i + "L");
            when(p.getName()).thenReturn("NAME_" + i + "L");
            list.add(p);
        }
        return Collections.unmodifiableList(list);
    }

    @Test
    public void issuePanelshouldDisplayEvenGerritWhitelistIsOff() {
        final GerritConfiguration gerritConfiguration = mock(GerritConfiguration.class);
        when(gerritConfiguration.getUseGerritProjectWhitelist()).thenReturn(false);
        final Issue issue = mock(Issue.class);
        when(gerritConfiguration.getShowsEmptyPanel()).thenReturn(true);
        Map<String, Object> map = new HashMap<>();
        map.put("issue", issue);
        ShowReviewsWebPanelCondition showReviewsWebPanelCondition = new ShowReviewsWebPanelCondition(null,
                gerritConfiguration);
        final boolean shouldDisplay = showReviewsWebPanelCondition.shouldDisplay(map);
        Assert.assertThat(shouldDisplay, Is.is(true));
    }
}