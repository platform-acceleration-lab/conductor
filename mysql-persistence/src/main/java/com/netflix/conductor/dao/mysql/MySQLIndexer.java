package com.netflix.conductor.dao.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.dao.IndexDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.List;

@Singleton
public class MySQLIndexer extends MySQLBaseDAO {

    private static final Logger logger = LoggerFactory.getLogger(MySQLIndexer.class);

    private final IndexDAO indexDAO;

    @Inject
    public MySQLIndexer(ObjectMapper om, DataSource dataSource, IndexDAO indexDAO) {
        super(om, dataSource);
        this.indexDAO = indexDAO;
    }

    public void reindex() {

        logger.debug("getting tasks");
        List<Task> tasks = getTasks();

        tasks.forEach(task -> {
            logger.debug("indexing task: {}", task);
            indexDAO.indexTask(task);
        });

        logger.debug("getting workflows");
        List<Workflow> workflows = getWorkflows();

        workflows.forEach(workflow -> {
            logger.debug("indexing workflow: {}", workflow);
            indexDAO.indexWorkflow(workflow);
        });

    }

    private List<Task> getTasks() {
        final String GET_TASKS = "SELECT json_data FROM task WHERE json_data IS NOT NULL";

        return getWithRetriedTransactions(c -> query(c, GET_TASKS, q -> q.executeAndFetch(Task.class)));
    }

    private List<Workflow> getWorkflows() {
        String GET_WORKFLOWS = "SELECT json_data FROM workflow";

        return getWithRetriedTransactions(c -> query(c, GET_WORKFLOWS, q -> q.executeAndFetch(Workflow.class)));
    }

}