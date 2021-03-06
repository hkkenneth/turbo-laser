package uk.ac.kcl.it;

import org.apache.log4j.Logger;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.kcl.batch.JobConfiguration;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.Random;

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

/**
 *
 * @author rich
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource({
        "classpath:postgres_db.properties",
        "classpath:postgres_test.properties",})
@Configuration
@Import({JobConfiguration.class,TestUtils.class})
@Profile("postgres")
@Ignore
public class PostGresTestUtils implements DbmsTestUtils{

    final static Logger logger = Logger.getLogger(GATEPKPartitionWithoutScheduling.class);
    long today = System.currentTimeMillis();
    Random random = new Random();



    @Autowired
    @Qualifier("sourceDataSource")
    public DataSource sourceDataSource;

    @Autowired
    @Qualifier("targetDataSource")
    public DataSource targetDataSource;

    @Autowired
    @Qualifier("jobRepositoryDataSource")
    public DataSource jobRepositoryDataSource;

    private JdbcTemplate sourceTemplate;
    private JdbcTemplate targetTemplate;
    private ResourceDatabasePopulator rdp = new ResourceDatabasePopulator();


    @PostConstruct
    public void init(){
        this.sourceTemplate = new JdbcTemplate(sourceDataSource);
        this.targetTemplate = new JdbcTemplate(targetDataSource);
        JdbcTemplate jobRepoTemplate = new JdbcTemplate(jobRepositoryDataSource);
    }


    public void createTikaTable() {
////        for postgres
        sourceTemplate.execute("DROP TABLE IF EXISTS tblInputDocs");
        sourceTemplate.execute("CREATE TABLE tblInputDocs"
                + "( ID  SERIAL PRIMARY KEY"
                + ", srcColumnFieldName text "
                + ", srcTableName text "
                + ", primaryKeyFieldName text "
                + ", primaryKeyFieldValue integer "
                + ", updateTime TIMESTAMP "
                + ", someText bytea"
                + ", anotherTime TIMESTAMP )");

        targetTemplate.execute("DROP TABLE IF EXISTS tblOutputDocs");
        targetTemplate.execute("CREATE TABLE tblOutputDocs "
                + "( ID  SERIAL PRIMARY KEY"
                + ", srcColumnFieldName text "
                + ", srcTableName text "
                + ", primaryKeyFieldName text "
                + ", primaryKeyFieldValue integer "
                + ", updateTime TIMESTAMP "
                + ", output text )");
    }



    public void createBasicInputTable(){
        sourceTemplate.execute("DROP TABLE IF EXISTS tblInputDocs");
        sourceTemplate.execute("CREATE TABLE tblInputDocs"
                + "( ID  SERIAL PRIMARY KEY"
                + ", srcColumnFieldName text "
                + ", srcTableName text "
                + ", primaryKeyFieldName text "
                + ", primaryKeyFieldValue integer "
                + ", updateTime TIMESTAMP "
                + ", someText TEXT"
                + ", anotherTime TIMESTAMP )");


    }

    public void createBasicOutputTable(){
        targetTemplate.execute("DROP TABLE IF EXISTS tblOutputDocs");
        targetTemplate.execute("CREATE TABLE tblOutputDocs "
                + "( ID  SERIAL PRIMARY KEY"
                + ", srcColumnFieldName text "
                + ", srcTableName text "
                + ", primaryKeyFieldName text "
                + ", primaryKeyFieldValue integer "
                + ", updateTime TIMESTAMP "
                + ", output text "
                + ", anotherTime TIMESTAMP)"
        );


    }

    public void createMultiLineTextTable(){
        createBasicInputTable();
        sourceTemplate.execute("DROP TABLE IF EXISTS tblDocLines");
        sourceTemplate.execute("CREATE TABLE tblDocLines"
                + "( ID  SERIAL PRIMARY KEY"
                + ", primaryKeyFieldValue integer "
                + ", updateTime TIMESTAMP "
                + ", LINE_ID integer "
                + ", LINE_TEXT text )"
        );
    }



    public void createJobRepository(){
        Resource dropTablesResource = new ClassPathResource("org/springframework/batch/core/schema-drop-postgresql" +
                ".sql");
        Resource makeTablesResource = new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql");
        rdp.addScript(dropTablesResource);
        rdp.addScript(makeTablesResource);
        rdp.execute(jobRepositoryDataSource);
    }

    @Override
    public void createDeIdInputTable() {
        createBasicInputTable();
        sourceTemplate.execute("DROP VIEW IF EXISTS vwidentifiers");
        sourceTemplate.execute("DROP TABLE IF EXISTS tblIdentifiers");
        sourceTemplate.execute("CREATE TABLE tblIdentifiers "
                + "( ID  SERIAL PRIMARY KEY"
                + ", primaryKeyFieldValue BIGINT "
                + ", NAME TEXT "
                + ", ADDRESS TEXT "
                + ", POSTCODE TEXT "
                + ", DATE_OF_BIRTH TIMESTAMP )");
        sourceTemplate.execute("create view vwIdentifiers AS\n" +
                "  select primarykeyfieldvalue, address as identifier from tblidentifiers\n" +
                "  UNION\n" +
                "  select primarykeyfieldvalue, name  as identifier from tblidentifiers\n" +
                "  UNION\n" +
                "  select primarykeyfieldvalue, postcode as identifier  from tblidentifiers");
    }

    @Override
    public int countRowsInOutputTable() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(targetDataSource);
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tblOutputDocs", Integer.class);
    }


}
