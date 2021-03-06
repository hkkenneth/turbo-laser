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
package uk.ac.kcl.batch;


import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.integration.partition.BeanFactoryStepLocator;
import org.springframework.batch.integration.partition.StepExecutionRequestHandler;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.support.DatabaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import uk.ac.kcl.exception.BiolarkProcessingFailedException;
import uk.ac.kcl.itemProcessors.JSONMakerItemProcessor;
import uk.ac.kcl.model.Document;
import uk.ac.kcl.utils.LoggerHelper;

import javax.sql.DataSource;
import java.awt.event.ItemListener;

/**
 *
 * @author rich
 */

@Configuration
@ComponentScan({"uk.ac.kcl.rowmappers",
        "uk.ac.kcl.utils",
        "uk.ac.kcl.listeners",
        "uk.ac.kcl.itemHandlers",
        "uk.ac.kcl.partitioners",
        "uk.ac.kcl.itemProcessors",
        "uk.ac.kcl.cleanup"})
@EnableBatchProcessing
@Import({
        BatchConfigurer.class,
        RemoteConfiguration.class,
        LocalConfiguration.class
})
public class JobConfiguration {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JobConfiguration.class);


    @Bean
    public LoggerHelper loggerHelper(){
        LoggerHelper lh = new LoggerHelper();
        lh.setContextID(env.getProperty("jobName"));
        return lh;
    }

    /*
        
    
    *******************************************COMMON BEANS
        
    
    */
    //required to process placeholder values in annotations, e.g. scheduler cron
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeHolderConfigurer() {
        PropertySourcesPlaceholderConfigurer props = new PropertySourcesPlaceholderConfigurer();
        props.setNullValue("");
        return props;
    }

    @Autowired
    public Environment env;

    @Bean
    @Qualifier("slaveTaskExecutor")
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor exec = new SimpleAsyncTaskExecutor();
        exec.setConcurrencyLimit(Integer.parseInt(env.getProperty("concurrencyLimit")));
        return exec;
    }



    @Bean(destroyMethod = "close")
    @Primary
    @Qualifier("sourceDataSource")
    //@Scope("prototype")
    public DataSource sourceDataSource() {
        HikariDataSource mainDatasource = new HikariDataSource();
        executeSessionScripts(mainDatasource,env.getProperty("source.Driver"));
        mainDatasource.setDriverClassName(env.getProperty("source.Driver"));
        mainDatasource.setJdbcUrl(env.getProperty("source.JdbcPath"));
        mainDatasource.setUsername(env.getProperty("source.username"));
        mainDatasource.setPassword(env.getProperty("source.password"));
        mainDatasource.setIdleTimeout(Long.valueOf(env.getProperty("source.idleTimeout")));
        mainDatasource.setMaxLifetime(Long.valueOf(env.getProperty("source.maxLifetime")));
        //mainDatasource.setConnectionTestQuery("select 1");

        return mainDatasource;
    }


    @Bean(destroyMethod = "close")
    //@Scope("prototype")
    @Qualifier("targetDataSource")
    public DataSource targetDataSource() {
        HikariDataSource mainDatasource = new HikariDataSource();
        executeSessionScripts(mainDatasource,env.getProperty("target.Driver"));
        mainDatasource.setDriverClassName(env.getProperty("target.Driver"));
        mainDatasource.setJdbcUrl(env.getProperty("target.JdbcPath"));
        mainDatasource.setUsername(env.getProperty("target.username"));
        mainDatasource.setPassword(env.getProperty("target.password"));
        mainDatasource.setIdleTimeout(Long.valueOf(env.getProperty("target.idleTimeout")));
        mainDatasource.setMaxLifetime(Long.valueOf(env.getProperty("target.maxLifetime")));
        //mainDatasource.setConnectionTestQuery("select 1");
        //?? should be managed by transaction manager
        //mainDatasource.setAutoCommit(false);
        return mainDatasource;

    }

//    @Bean
//    @Profile("jdbc")
//    @Qualifier("targetDatasourceTransactionManager")
//    public PlatformTransactionManager targetDatasourceTransactionManager(
//            @Qualifier("targetDataSource")
//                    DataSource jdbcDocumentTarget) {
//        DataSourceTransactionManager tx = new DataSourceTransactionManager();
//        tx.setDataSource(jdbcDocumentTarget);
//        return tx;
//    }





    private void executeSessionScripts(HikariDataSource mainDatasource, String driver) {
        //temp datasource required to get type
        DatabaseType type = null;

            switch (driver) {
                case "DERBY":
                    break;
                case "DB2":
                    break;
                case "DB2ZOS":
                    break;
                case "HSQL":
                    break;
                case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                    mainDatasource.setConnectionInitSql("SET DATEFORMAT ymd;");
                    break;
                case "MYSQL":
                    break;
                case "ORACLE":
                    break;
                case "POSTGRES":
                    break;
                case "SYBASE":
                    break;
                case "H2":
                    break;
                case "SQLITE":
                    break;
            }

    }

    @Value("${target.username}")
    String targetUserName;
    @Value("${target.password}")
    String targetPassword;







//    

    @Bean
    public BeanFactoryStepLocator stepLocator(){
        return new BeanFactoryStepLocator();
    }





    @Bean
    public StepExecutionRequestHandler stepExecutionRequestHandler(
            JobExplorer jobExplorer, BeanFactoryStepLocator stepLocator){
        StepExecutionRequestHandler handler = new StepExecutionRequestHandler();
        handler.setJobExplorer(jobExplorer);
        handler.setStepLocator(stepLocator);
        return handler;
    }

    @Bean
    @Qualifier("jsonMakerItemProcessor")
    public JSONMakerItemProcessor jsonMakerItemProcessor(){
        return new JSONMakerItemProcessor();
    }

    @Bean
    @Qualifier("runIdIncrementer")
    public RunIdIncrementer runIdIncrementer(){return new RunIdIncrementer();}




    @Bean
    @Qualifier("compositeSlaveStep")
    public Step compositeSlaveStep(
                        ItemReader<Document> reader,
            @Qualifier("compositeItemProcessor") ItemProcessor<Document, Document> processor,
            @Qualifier("compositeESandJdbcItemWriter") ItemWriter<Document> writer,
            @Qualifier("slaveTaskExecutor")TaskExecutor taskExecutor,
            @Qualifier("nonFatalExceptionItemProcessorListener")
                                ItemProcessListener nonFatalExceptionItemProcessorListener,
            //@Qualifier("targetDatasourceTransactionManager")PlatformTransactionManager manager,
            StepBuilderFactory stepBuilderFactory
    ) {
        return stepBuilderFactory.get("compositeSlaveStep")
                .<Document, Document> chunk(
                        Integer.parseInt(env.getProperty("chunkSize")))
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(Integer.parseInt(env.getProperty("skipLimit")))
                .skip(BiolarkProcessingFailedException.class)
                .noSkip(Exception.class)
         //       .listener(nonFatalExceptionItemProcessorListener)
                .taskExecutor(taskExecutor)
                .build();
    }
}
