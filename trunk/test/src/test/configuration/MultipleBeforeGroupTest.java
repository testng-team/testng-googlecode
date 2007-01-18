package test.configuration;

import org.testng.Assert;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

@Test( groups = "foo" )
public class MultipleBeforeGroupTest {
  private int m_count = 0;
  
  @BeforeGroups( "foo" )
  public void beforeGroups() {
    m_count++;
    System.out.println( "beforeGroups" );
  }

  @Test()
  public void test() {
      System.out.println( "test" );
  }
  
  @Test(dependsOnMethods = "test")
  public void verify() {
    Assert.assertEquals(1, m_count);
  }

}
