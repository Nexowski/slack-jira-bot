package com.mlorenc.slack.jira.bot.repository;

import com.mlorenc.slack.jira.bot.model.ProjectFieldMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProjectFieldMappingRepositoryTest {

    @Autowired
    private ProjectFieldMappingRepository repository;

    @Test
    void shouldPersistProjectFieldMappings() {
        ProjectFieldMapping mapping = new ProjectFieldMapping();
        mapping.setSlackUserId("U1");
        mapping.setJiraProjectKey("ABC");
        mapping.setProgressFieldId("customfield_1001");
        repository.save(mapping);

        assertThat(repository.findBySlackUserIdAndJiraProjectKey("U1", "ABC")).isPresent();
    }
}
