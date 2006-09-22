package test.tmp;

import org.testng.annotations.AfterGroups;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class A {
  @BeforeGroups(groups={"twice"}, value={"twice"})
  public void a(){
    ppp("BEFORE()");
  }

  @Test(groups={"twice"}, dataProvider="MyData")
  public void b(int a, int b) {
//  @Test(groups = {"twice"}, invocationCount = 2)
//  public void b() {
    ppp("B()");
//    ppp("B() " + a + "," + b);
  }

  @AfterGroups(groups={"twice"}, value={"twice"})
  public void c(){
    ppp("AFTER()");
  }

  @DataProvider(name="MyData")
  public Object[][] input(){
    return new Object[][]{ {1,1}, {2,2}, {3,3}};
  }
  
  private void ppp(String string) {
    System.out.println("[A] " + string);
  }
  

}
