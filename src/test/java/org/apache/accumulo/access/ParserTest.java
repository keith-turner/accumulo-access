package org.apache.accumulo.access;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ParserTest {
    @Test
    public void testParsing() throws Exception {
        List<AccessEvaluatorTest.TestDataSet> testdata = AccessEvaluatorTest.readTestData();


        for (var testset : testdata) {

            for(var tests : testset.tests) {
                for(var expression : tests.expressions) {
                    System.out.println("Testing '"+expression+"' "+tests.expectedResult);


                    switch (tests.expectedResult) {
                        case ACCESSIBLE:
                        case INACCESSIBLE:
                            Parser.parseAccessExpression(expression);
                            break;
                        case ERROR:
                            Assertions.assertThrows(RuntimeException.class, ()->Parser.parseAccessExpression(expression), expression);
                            break;
                    }
                }
            }
        }
    }
}
