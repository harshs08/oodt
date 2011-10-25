/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.oodt.cas.workflow.engine;

//JDK imports
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

//OODT imports
import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.cas.resource.system.XmlRpcResourceManagerClient;
import org.apache.oodt.cas.workflow.instrepo.WorkflowInstanceRepository;
import org.apache.oodt.cas.workflow.structs.WorkflowInstance;
import org.apache.oodt.cas.workflow.structs.WorkflowTask;
import org.apache.oodt.cas.workflow.structs.WorkflowTaskConfiguration;
import org.apache.oodt.cas.workflow.structs.WorkflowTaskInstance;
import org.apache.oodt.cas.workflow.structs.exceptions.InstanceRepositoryException;

/**
 * An abstract base class representing the methodology for processing a
 * {@link WorkflowInstance}. The job of this class is to actually take the
 * WorkflowInstance and execute its jobs. The class should maintain the state of
 * the instance, such as the currentTaskId, and so forth.
 * 
 */
public abstract class WorkflowProcessor {

  private static final Logger LOG = Logger.getLogger(WorkflowProcessor.class
      .getName());

  protected WorkflowInstance workflowInstance;

  protected long waitForConditionSatisfy = -1;

  protected WorkflowInstanceRepository instanceRepository = null;

  protected XmlRpcResourceManagerClient rClient = null;

  protected long pollingWaitTime = 10L;

  protected boolean running = false;

  protected int timesPaused;

  protected static final String DEFAULT_QUEUE_NAME = "high";

  protected URL wmgrParentUrl = null;

  protected String currentJobId = null;

  protected boolean paused = false;

  public WorkflowProcessor(WorkflowInstance workflowInstance,
      WorkflowInstanceRepository instRep, URL wParentUrl, long conditionWait) {
    this.workflowInstance = workflowInstance;
    this.instanceRepository = instRep;
    this.running = true;
    this.waitForConditionSatisfy = conditionWait;
    this.pollingWaitTime = conditionWait;
    this.wmgrParentUrl = wParentUrl;

  }

  /**
   * @return the workflowInstance
   */
  public WorkflowInstance getWorkflowInstance() {
    return workflowInstance;
  }

  /**
   * @param workflowInstance
   *          the workflowInstance to set
   */
  public void setWorkflowInstance(WorkflowInstance workflowInstance) {
    this.workflowInstance = workflowInstance;
  }

  /**
   * <p>
   * Stops once and for all the thread from processing the workflow. This method
   * should not maintain the state of the workflow, it should gracefully shut
   * down the WorkflowProcessor and any of its subsequent resources.
   * </p>
   * 
   */
  public abstract void stop();

  /**
   * <p>
   * Resumes execution of a {@link #pause}d {@link WorkflowInstace} by this
   * WorkflowProcessor.
   * </p>
   * 
   */
  public abstract void resume();

  /**
   * <p>
   * Pauses exectuion of a {@link WorkflowInstace} being handled by this
   * WorkflowProcessor.
   * </p>
   * 
   */
  public abstract void pause();
  
  
  /**
   * Starts execution of the subordinate {@link WorkflowProcessor}.
   */
  public abstract void start();

  /**
   * Returns the identifier of the current {@link WorkflowTask} being processed
   * by this WorkflowProcessor.
   * 
   * @return the identifier of the current {@link WorkflowTask} being processed
   *         by this WorkflowProcessor.
   */
  public String getCurrentTaskId() {
    return this.workflowInstance.getCurrentTaskId();
  }

  /**
   * @return the running
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * @param running
   *          the running to set
   */
  public void setRunning(boolean running) {
    this.running = running;
  }

  /**
   * @return the paused
   */
  public boolean isPaused() {
    return paused;
  }

  /**
   * @param paused
   *          the paused to set
   */
  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  /**
   * @return the rClient
   */
  public XmlRpcResourceManagerClient getrClient() {
    return rClient;
  }

  /**
   * @param client
   *          the rClient to set
   */
  public void setRClient(XmlRpcResourceManagerClient client) {
    rClient = client;
    if (rClient != null) {
      LOG.log(Level.INFO, "Resource Manager Job Submission enabled to: ["
          + rClient.getResMgrUrl() + "]");
    }
  }

  protected void persistWorkflowInstance() {
    try {
      instanceRepository.updateWorkflowInstance(this.workflowInstance);
    } catch (InstanceRepositoryException e) {
      LOG.log(Level.WARNING, "Exception persisting workflow instance: ["
          + this.workflowInstance.getId() + "]: Message: " + e.getMessage());
    }
  }

  protected void executeTaskLocally(WorkflowTaskInstance instance,
      Metadata met, WorkflowTaskConfiguration cfg, String taskName) {
    try {
      LOG.log(Level.INFO, "Executing task: [" + taskName + "] locally");
      instance.run(met, cfg);
    } catch (Exception e) {
      e.printStackTrace();
      LOG.log(Level.WARNING, "Exception executing task: [" + taskName
          + "] locally: Message: " + e.getMessage());
    }
  }

  protected boolean safeCheckJobComplete(String jobId) {
    try {
      return rClient.isJobComplete(jobId);
    } catch (Exception e) {
      LOG.log(Level.WARNING, "Exception checking completion status for job: ["
          + jobId + "]: Messsage: " + e.getMessage());
      return false;
    }
  }

  protected boolean checkTaskRequiredMetadata(WorkflowTask task,
      Metadata dynMetadata) {
    if (task.getRequiredMetFields() == null
        || (task.getRequiredMetFields() != null && task.getRequiredMetFields()
            .size() == 0)) {
      LOG.log(Level.INFO, "Task: [" + task.getTaskName()
          + "] has no required metadata fields");
      return true; /* no required metadata, so we're fine */
    }

    for (String reqField : (List<String>) (List<?>) task.getRequiredMetFields()) {
      if (!dynMetadata.containsKey(reqField)) {
        LOG.log(Level.SEVERE, "Checking metadata key: [" + reqField
            + "] for task: [" + task.getTaskName()
            + "]: failed: aborting workflow");
        return false;
      }
    }

    LOG.log(Level.INFO, "All required metadata fields present for task: ["
        + task.getTaskName() + "]");

    return true;
  }

  protected String getTaskNameById(String taskId) {
    for (WorkflowTask task : (List<WorkflowTask>) (List<?>) this.workflowInstance
        .getWorkflow().getTasks()) {
      if (task.getTaskId().equals(taskId)) {
        return task.getTaskName();
      }
    }

    return null;
  }

  protected String getHostname() {
    try {
      // Get hostname by textual representation of IP address
      InetAddress addr = InetAddress.getLocalHost();
      // Get the host name
      String hostname = addr.getHostName();
      return hostname;
    } catch (UnknownHostException e) {
    }
    return null;
  }

}