/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import com.netflix.conductor.annotations.Trace;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.ApplicationException.Code;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dyno.DynoProxy;
import com.netflix.dyno.jedis.DynoJedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
@Singleton
@Trace
public class RedisExecutionIndexer extends BaseDynoDAO {

    public static final Logger logger = LoggerFactory.getLogger(RedisExecutionIndexer.class);


    private static final String ARCHIVED_FIELD = "archived";
    private static final String RAW_JSON_FIELD = "rawJSON";
    // Keys Families
    private static final String TASK_LIMIT_BUCKET = "TASK_LIMIT_BUCKET";
    private static final String TASK_RATE_LIMIT_BUCKET = "TASK_RATE_LIMIT_BUCKET";
    private final static String IN_PROGRESS_TASKS = "IN_PROGRESS_TASKS";
    private final static String TASKS_IN_PROGRESS_STATUS = "TASKS_IN_PROGRESS_STATUS";    //Tasks which are in IN_PROGRESS status.
    private final static String WORKFLOW_TO_TASKS = "WORKFLOW_TO_TASKS";
    private final static String SCHEDULED_TASKS = "SCHEDULED_TASKS";
    private final static String TASK = "TASK";

    private final static String WORKFLOW = "WORKFLOW";
    private final static String PENDING_WORKFLOWS = "PENDING_WORKFLOWS";
    private final static String WORKFLOW_DEF_TO_WORKFLOWS = "WORKFLOW_DEF_TO_WORKFLOWS";
    private final static String CORR_ID_TO_WORKFLOWS = "CORR_ID_TO_WORKFLOWS";
    private final static String POLL_DATA = "POLL_DATA";

    private final static String EVENT_EXECUTION = "EVENT_EXECUTION";
    private final DynoJedisClient dynoJedisClient;
    private IndexDAO indexDAO;

    @Inject
    public RedisExecutionIndexer(DynoProxy dynoClient, ObjectMapper objectMapper,
                                 IndexDAO indexDAO, Configuration config,
                                 DynoJedisClient dynoJedisClient) {
        super(dynoClient, objectMapper, config);
        this.indexDAO = indexDAO;
        this.dynoJedisClient = dynoJedisClient;
    }

    public void indexRedis() {

        Set<String> taskKeys = dynoJedisClient.keys(nsKey(TASK, "*"));
        logger.info("Keys to index found at {}: {}", nsKey(TASK, "*"), taskKeys.size());

        taskKeys.stream()
                .map(taskKey -> taskKey.replace(nsKey(TASK) + ".", ""))
                .map(taskId -> getTask(taskId))
                .forEach(task -> {
                    logger.debug("Indexing task: {}", task);
                    indexDAO.indexTask(task);
                });

        Set<String> workflowKeys = dynoJedisClient.keys(nsKey(WORKFLOW, "*"));
        workflowKeys.stream()
                .map(workflowKey -> workflowKey.replace(nsKey(WORKFLOW) + ".", ""))
                .map(workflowId -> getWorkflow(workflowId))
                .forEach(workflow -> {
                    logger.debug("Indexing workflow: {}", workflow);
                    indexDAO.indexWorkflow(workflow);
                });
    }

    private Task getTask(String taskId) {
        Preconditions.checkNotNull(taskId, "taskId name cannot be null");
        String key = nsKey(TASK, taskId);
        Task task = Optional.ofNullable(dynoClient.get(key))
                .map(jsonString -> readValue(jsonString, Task.class))
                .orElse(null);
        if (task != null) {
            recordRedisDaoRequests("getTask", task.getTaskType(), task.getWorkflowType());
            recordRedisDaoPayloadSize("getTask", toJson(task).length(), task.getTaskType(), task.getWorkflowType());
        }
        return task;
    }

    private List<Task> getTasks(List<String> taskIds) {
        return taskIds.stream()
                .map(taskId -> nsKey(TASK, taskId))
                .map(dynoClient::get)
                .filter(Objects::nonNull)
                .map(jsonString -> {
                    Task task = readValue(jsonString, Task.class);
                    recordRedisDaoRequests("getTask", task.getTaskType(), task.getWorkflowType());
                    recordRedisDaoPayloadSize("getTask", jsonString.length(), task.getTaskType(), task.getWorkflowType());
                    return task;
                })
                .collect(Collectors.toList());
    }

    private List<Task> getTasksForWorkflow(String workflowId) {
        Preconditions.checkNotNull(workflowId, "workflowId cannot be null");
        Set<String> taskIds = dynoClient.smembers(nsKey(WORKFLOW_TO_TASKS, workflowId));
        recordRedisDaoRequests("getTasksForWorkflow");
        return getTasks(new ArrayList<>(taskIds));
    }

    private Workflow getWorkflow(String workflowId) {
        return getWorkflow(workflowId, true);
    }

    private Workflow getWorkflow(String workflowId, boolean includeTasks) {
        String json = dynoClient.get(nsKey(WORKFLOW, workflowId));
        Workflow workflow;

        if (json != null) {
            workflow = readValue(json, Workflow.class);
            recordRedisDaoRequests("getWorkflow", "n/a", workflow.getWorkflowName());
            recordRedisDaoPayloadSize("getWorkflow", json.length(), "n/a", workflow.getWorkflowName());
            if (includeTasks) {
                List<Task> tasks = getTasksForWorkflow(workflowId);
                tasks.sort(Comparator.comparingLong(Task::getScheduledTime).thenComparingInt(Task::getSeq));
                workflow.setTasks(tasks);
            }
        } else {
            // record dao request metric here, since request is still made to the db even if non-existent key
            recordRedisDaoRequests("getWorkflow");

            // try from the archive
            // Expected to include tasks.
            json = indexDAO.get(workflowId, RAW_JSON_FIELD);
            if (json == null) {
                throw new ApplicationException(Code.NOT_FOUND, "No such workflow found by id: " + workflowId);
            }
            workflow = readValue(json, Workflow.class);
        }

        if (!includeTasks) {
            workflow.getTasks().clear();
        }
        return workflow;
    }

}
