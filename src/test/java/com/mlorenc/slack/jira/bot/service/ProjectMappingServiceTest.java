package com.mlorenc.slack.jira.bot.service;

import com.mlorenc.slack.jira.bot.model.ProjectFieldMapping;
import com.mlorenc.slack.jira.bot.repository.ProjectFieldMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMappingServiceTest {

    @Mock
    private ProjectFieldMappingRepository repository;

    @InjectMocks
    private ProjectMappingService service;

    @Test
    void shouldSaveSelectedFieldIdAndUppercaseProject() {
        when(repository.findBySlackUserIdAndJiraProjectKey("U1", "abc")).thenReturn(Optional.empty());

        service.saveMapping("U1", "abc", "customfield_10042");

        ArgumentCaptor<ProjectFieldMapping> captor = ArgumentCaptor.forClass(ProjectFieldMapping.class);
        verify(repository).save(captor.capture());

        ProjectFieldMapping saved = captor.getValue();
        assertThat(saved.getSlackUserId()).isEqualTo("U1");
        assertThat(saved.getJiraProjectKey()).isEqualTo("ABC");
        assertThat(saved.getProgressFieldId()).isEqualTo("customfield_10042");
    }
}
