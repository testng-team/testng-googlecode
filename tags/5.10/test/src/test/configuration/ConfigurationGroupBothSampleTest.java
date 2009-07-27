package test.configuration;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ConfigurationGroupBothSampleTest {
  static List<Integer> m_list = new ArrayList<Integer>();
  
  @BeforeGroups(groups={"twice"}, value={"twice"})
  public void a(){
    ppp("BEFORE()");
    m_list.add(1);
  }

  @Test(groups={"twice"}, dataProvider="MyData", invocationCount = 2, threadPoolSize=2)
  public void b(int a, int b) {
    m_list.add(2);
    ppp("B()"  + a + "," + b);
  }

  @AfterGroups(groups={"twice"}, value={"twice"})
  public void c(){
    m_list.add(3);
    ppp("AFTER()");
  }
  
  @DataProvider(name="MyData")
  public Object[][] input(){
    return new Object[][]{ {1,1}, {2,2}, {3,3}};
  }
  
  private void ppp(String string) {
    if (false) {
      System.out.println("[A] " + string + " on Thread:" + Thread.currentThread());
    }
  }
  

}
