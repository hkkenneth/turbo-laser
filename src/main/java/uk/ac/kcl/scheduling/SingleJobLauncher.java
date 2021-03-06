/*
 * Copyright 2016 King's College London, Richard Jackson <richgjackson@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.kcl.scheduling;

import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.*;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import uk.ac.kcl.batch.JobConfiguration;
import uk.ac.kcl.cleanup.CleanupBean;
import uk.ac.kcl.utils.BatchJobUtils;

import javax.sql.DataSource;
import java.util.List;


/**
 *
 * @author King's College London, Richard Jackson <richgjackson@gmail.com>
 */
@Service
@Import(JobConfiguration.class)
@ComponentScan({"uk.ac.kcl.utils"})
public class SingleJobLauncher {


    @Autowired
    Environment env;

    @Autowired(required=false)
    JobLauncher jobLauncher;

    @Autowired(required=false)
    Job job;

    @Autowired(required=false)
    BatchJobUtils batchJobUtils;

    @Autowired
    @Qualifier("targetDataSource")
    DataSource targetDataSource;

    @Autowired
    @Qualifier("sourceDataSource")
    DataSource sourceDataSource;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobExplorer jobExplorer;

    @Autowired
    JobOperator jobOperator;


    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SingleJobLauncher.class);


    public void launchJob()  {
        JobExecution lastJobExecution = getLastJobExecution();
        try {
            if(lastJobExecution != null){
                BatchStatus lastJobStatus = lastJobExecution.getStatus();
                switch (lastJobStatus) {
                    case COMPLETED:
                        LOG.info("Last job execution was successful");
                        startNextInstance();
                        break;
                    case STARTED:
                    case STARTING:
                    case STOPPING:
                        LOG.info("Job is already running. Repository in unknown state." +
                                " Attempting to repair and restart from last successful job");
                        abandonAllJobsStartedAfterLastSuccessfulJob();
                        startNextInstance();
                        break;
                    case FAILED:
                        LOG.info("Last job failed. Attempting restart");
                        startNextInstance();
                        break;
                    case ABANDONED:
                        LOG.info("Last job was abandoned. Attempting start from last successful job");
                        abandonAllJobsStartedAfterLastSuccessfulJob();
                        startNextInstance();
                        break;
                    case STOPPED:
                        LOG.info("Last job was stopped. Attempting restart");
                        startNextInstance();
                        break;
                    case UNKNOWN:
                        LOG.info("Last job has unknown status. Marking as abandoned and attempting restart from last successful job");
                        abandonAllJobsStartedAfterLastSuccessfulJob();
                        startNextInstance();
                        break;
                    default:
                        LOG.error("Should be unreachable");
                        break;
                }
            }else{
                LOG.info("No previous completed jobs found");
                startNextInstance();
            }
        } catch (JobInstanceAlreadyCompleteException|
                JobExecutionAlreadyRunningException|
                JobParametersInvalidException
                e) {
            LOG.error("Cannot start job", e);
        } catch (JobRestartException e){
            LOG.error("Cannot restart job. Attempting start from last successful job", e);
            try {
                jobOperator.abandon(lastJobExecution.getId());
                startNextInstance();
            } catch (NoSuchJobExecutionException |JobExecutionAlreadyRunningException|
                    NoSuchJobException|JobInstanceAlreadyCompleteException|
                    JobRestartException|JobParametersNotFoundException|JobParametersInvalidException e1) {
                throw new RuntimeException("Cannot start next instance", e1);
            }
        }catch (Exception e) {
            LOG.error("Cannot start job", e);
        }
    }

    private void startNextInstance() throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException,
            JobParametersInvalidException, JobRestartException, JobParametersNotFoundException, NoSuchJobException {
        jobOperator.startNextInstance(job.getName());

    }
    private void abandonAllJobsStartedAfterLastSuccessfulJob() {
        List<Long> idsToAbandon = batchJobUtils.getExecutionIdsOfJobsToAbandon();

        for (Long id : idsToAbandon) {
            try {
                try {

                    jobOperator.stop(id);
                } catch (JobExecutionNotRunningException e) {
                    LOG.info("Cannot stop job execution ID " + id +
                            " as it is already stopped. Attempting to mark as abandoned");
                }
                try {
                    jobOperator.abandon(id);
                } catch (JobExecutionAlreadyRunningException e) {
                    throw new RuntimeException("Cannot abandon job execution ID " + id + " as it appears to be running. "+
                            "JobRepository may require inspection",e);
                }
            } catch (NoSuchJobExecutionException e) {
                throw new RuntimeException("Cannot mark job execution ID " + id + " as abandoned as it doesn't exist." +
                        " JobRepository may require inspection)", e);
            }
        }
    }

    private JobExecution getLastJobExecution(){
        JobExecution lastJobExecution = null;
        try {
            lastJobExecution = batchJobUtils.getLastSuccessfulJobExecution();
        } catch (NullPointerException e) {
            LOG.info("No previous successful jobs found");
        }
        if(lastJobExecution == null){
            try{
                lastJobExecution = batchJobUtils.getLastJobExecution();
            } catch (NullPointerException e) {
                LOG.info("No previous jobs found");
            }
        }
        return lastJobExecution;
    }

}
