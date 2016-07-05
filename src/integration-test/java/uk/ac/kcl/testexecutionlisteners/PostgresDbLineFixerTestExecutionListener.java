package uk.ac.kcl.testexecutionlisteners;


import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import uk.ac.kcl.it.PostGresTestUtils;
import uk.ac.kcl.it.TestUtils;

/**
 * Created by rich on 03/06/16.
 */
public class PostgresDbLineFixerTestExecutionListener extends AbstractTestExecutionListener {

    public PostgresDbLineFixerTestExecutionListener(){}

    @Override
    public void beforeTestClass(TestContext testContext) {
        PostGresTestUtils postGresTestUtils =
                testContext.getApplicationContext().getBean(PostGresTestUtils.class);
        postGresTestUtils.createJobRepository();
        postGresTestUtils.createBasicOutputTable();
        postGresTestUtils.createMultiLineTextTable();
        TestUtils testUtils =
                testContext.getApplicationContext().getBean(TestUtils.class);
        testUtils.insertDataIntoBasicTable("tblInputDocs");
        testUtils.insertTestLinesForDBLineFixer("tblDocLines");
    }

}