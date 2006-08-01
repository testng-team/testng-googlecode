package org.testng;


import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.testng.internal.ClassHelper;
import org.testng.internal.ConfigurationGroupMethods;
import org.testng.internal.Constants;
import org.testng.internal.IInvoker;
import org.testng.internal.ITestResultNotifier;
import org.testng.internal.InvokedMethod;
import org.testng.internal.Invoker;
import org.testng.internal.MethodHelper;
import org.testng.internal.PackageUtils;
import org.testng.internal.ResultMap;
import org.testng.internal.RunInfo;
import org.testng.internal.TestMethodWorker;
import org.testng.internal.TestNGClassFinder;
import org.testng.internal.TestNGMethod;
import org.testng.internal.TestNGMethodFinder;
import org.testng.internal.Utils;
import org.testng.internal.XmlMethodSelector;
import org.testng.internal.annotations.IAnnotationFinder;
import org.testng.internal.thread.IPooledExecutor;
import org.testng.internal.thread.ThreadUtil;
import org.testng.junit.JUnitClassFinder;
import org.testng.junit.JUnitMethodFinder;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlPackage;
import org.testng.xml.XmlTest;

/**
 * This class takes care of running one Test.
 *
 * @author Cedric Beust, Apr 26, 2004
 * @author <a href = "mailto:the_mindstorm&#64;evolva.ro">Alexandru Popescu</a>
 */
public class TestRunner implements ITestContext, ITestResultNotifier {
  /* generated */
  private static final long serialVersionUID = 4247820024988306670L;
  private ISuite m_suite;
  protected XmlTest m_xmlTest;
  private String m_testName;
  private boolean m_debug = false;

  transient private List<XmlClass> m_testClassesFromXml= null;
  transient private List<XmlPackage> m_packageNamesFromXml= null;

  transient private IInvoker m_invoker= null;
  transient private IAnnotationFinder m_annotationFinder= null;

  /** ITestListeners support. */
  transient private List<ITestListener> m_testListeners = new ArrayList<ITestListener>();

  /**
   * All the test methods we found, associated with their respective classes.
   * Note that these test methods might belong to different classes.
   * We pick which ones to run at runtime.
   */
  private ITestNGMethod[] m_allTestMethods = new ITestNGMethod[0];

  // Information about this test run

  private Date m_startDate = null;
  private Date m_endDate = null;

  /** A map to keep track of Class <-> IClass. */
  transient private Map<Class, ITestClass> m_classMap= new HashMap<Class, ITestClass>();

  /** Where the reports will be created. */
  private String m_outputDirectory= Constants.getDefaultValueFor(Constants.PROP_OUTPUT_DIR);

  // The XML method selector (groups/methods included/excluded in XML)
  private XmlMethodSelector m_xmlMethodSelector = new XmlMethodSelector();

  private static int m_verbose = 1;

  //
  // These next fields contain all the configuration methods found on this class.
  // At initialization time, they just contain all the various @Configuration methods
  // found in all the classes we are going to run.  When comes the time to run them,
  // only a subset of them are run:  those that are enabled and belong on the same class as
  // (or a parent of) the test class.
  //
  private ITestNGMethod[] m_beforeClassMethods = {};
  private ITestNGMethod[] m_afterClassMethods = {};
  /** */
  private ITestNGMethod[] m_beforeSuiteMethods = {};
  private ITestNGMethod[] m_afterSuiteMethods = {};
  private ITestNGMethod[] m_beforeXmlTestMethods = {};
  private ITestNGMethod[] m_afterXmlTestMethods = {};
  private List<ITestNGMethod> m_excludedMethods = new ArrayList<ITestNGMethod>();
  private ConfigurationGroupMethods m_groupMethods = null;

  // Meta groups
  private Map<String, List<String>> m_metaGroups = new HashMap<String, List<String>>();

  // All the tests that were run along with their result
  private IResultMap m_passedTests = new ResultMap();
  private IResultMap m_failedTests = new ResultMap();
  private IResultMap m_failedButWithinSuccessPercentageTests = new ResultMap();
  private IResultMap m_skippedTests = new ResultMap();

  private RunInfo m_runInfo= new RunInfo();
  
  // The host where this test was run, or null if run locally
  private String m_host;

  public TestRunner(ISuite suite,
                    XmlTest test,
                    String outputDirectory,
                    IAnnotationFinder finder) {
    init(suite, test, outputDirectory, finder);
  }

  public TestRunner(ISuite suite, XmlTest test, IAnnotationFinder finder) {
    init(suite, test, suite.getOutputDirectory(), finder);
  }

  public TestRunner(ISuite suite, XmlTest test) {
    init(suite, test, suite.getOutputDirectory(), SuiteRunner.getAnnotationFinder(test));
  }

  private void init(ISuite suite,
                    XmlTest test,
                    String outputDirectory,
                    IAnnotationFinder annotationFinder)
  {
    m_xmlTest= test;
    m_suite = suite;
    m_testName = test.getName();
    m_testClassesFromXml= test.getXmlClasses();
    m_packageNamesFromXml= new ArrayList<XmlPackage>(test.getXmlPackages());
    m_packageNamesFromXml.addAll(test.getSuite().getXmlPackages());
    m_host = suite.getHost();
    
    // If packages were supplied, find all the classes they contain
    // and add them directly to the m_testClassesFromXml field
    if (null != m_packageNamesFromXml) {
      for (XmlPackage xp : m_packageNamesFromXml) {
        String pkg = xp.getName();
        try {
          String[] classes = PackageUtils.findClassesInPackage(pkg, xp.getInclude(), xp.getExclude());
          for (String cls : classes) {
            XmlClass xc = new XmlClass(cls);
            m_testClassesFromXml.add(xc);
          }
        }
        catch(Exception e) {

          // Should ignore
          e.printStackTrace();
        }
      }
    }
    m_annotationFinder= annotationFinder;
    m_invoker= new Invoker(this, this, m_annotationFinder);

    setVerbose(test.getVerbose());

    if (suite.isParallel()) {
      log(3, "Running the tests in parallel");
    }

    setOutputDirectory(outputDirectory);

    // Finish our initialization
    init();
  }

  public IInvoker getInvoker() {
    return m_invoker;
  }

  public ITestNGMethod[] getBeforeSuiteMethods() {
    return m_beforeSuiteMethods;
  }

  public ITestNGMethod[] getAfterSuiteMethods() {
    return m_afterSuiteMethods;
  }

  public ITestNGMethod[] getBeforeTestConfigurationMethods() {
    return m_beforeXmlTestMethods;
  }

  public ITestNGMethod[] getAfterTestConfigurationMethods() {
    return m_afterXmlTestMethods;
  }

  private void init() {
    initMetaGroups(m_xmlTest);
    initRunInfo(m_xmlTest);

    // Init methods and class map
    initMethods();

  }

  /**
   * Initialize meta groups
   */
  private void initMetaGroups(XmlTest xmlTest) {
    Map<String, List<String>> metaGroups = xmlTest.getMetaGroups();

    for (String name : metaGroups.keySet()) {
      addMetaGroup(name, metaGroups.get(name));
    }
  }

  private void initRunInfo(final XmlTest xmlTest) {
    // Groups
    m_xmlMethodSelector.setIncludedGroups(createGroups(m_xmlTest.getIncludedGroups()));
    m_xmlMethodSelector.setExcludedGroups(createGroups(m_xmlTest.getExcludedGroups()));
    m_xmlMethodSelector.setExpression(m_xmlTest.getExpression());

    // Methods
    m_xmlMethodSelector.setXmlClasses(m_xmlTest.getXmlClasses());
    
    m_runInfo.addMethodSelector(m_xmlMethodSelector, 10);
    
    // Add user-specified method selectors (only class selectors, we can ignore
    // script selectors here)
    if (null != xmlTest.getMethodSelectors()) {
      for (org.testng.xml.XmlMethodSelector selector : xmlTest.getMethodSelectors()) {
        if (selector.getClassName() != null) {
          IMethodSelector s = ClassHelper.createSelector(selector);
          
          m_runInfo.addMethodSelector(s, selector.getPriority());
        }
      }
    }
  }

  private void initMethods() {

    //
    // Calculate all the methods we need to invoke
    //
    List<ITestNGMethod> beforeClassMethods = new ArrayList<ITestNGMethod>();
    List<ITestNGMethod> testMethods = new ArrayList<ITestNGMethod>();
    List<ITestNGMethod> afterClassMethods = new ArrayList<ITestNGMethod>();
    List<ITestNGMethod> beforeSuiteMethods = new ArrayList<ITestNGMethod>();
    List<ITestNGMethod> afterSuiteMethods = new ArrayList<ITestNGMethod>();
    List<ITestNGMethod> beforeXmlTestMethods = new ArrayList<ITestNGMethod>();
    List<ITestNGMethod> afterXmlTestMethods = new ArrayList<ITestNGMethod>();

    ITestMethodFinder testMethodFinder= null;
    ITestClassFinder testClassFinder= null;
    if (m_xmlTest.isJUnit()) {
      testClassFinder= new JUnitClassFinder(Utils.xmlClassesToClasses(m_testClassesFromXml),
                                            m_xmlTest,
                                            m_annotationFinder);
      testMethodFinder= new JUnitMethodFinder(m_testName, m_annotationFinder);
    }
    else {
      testClassFinder= new TestNGClassFinder(Utils.xmlClassesToClasses(m_testClassesFromXml),
                                             null,
                                             m_xmlTest,
                                             m_annotationFinder);
      testMethodFinder= new TestNGMethodFinder(m_runInfo, m_annotationFinder);
    }

    //
    // Initialize TestClasses
    //
    IClass[] classes = testClassFinder.findTestClasses();
    
    for (IClass ic : classes) {

      // Create TestClass
      ITestClass tc = new TestClass(ic,
                                   m_testName,
                                   testMethodFinder,
                                   m_annotationFinder,
                                   m_runInfo,
                                   this);
      m_classMap.put(ic.getRealClass(), tc);
    }
    
    //
    // Calculate groups methods
    //
    m_groupMethods = findGroupMethods(m_classMap.values());
   

    //
    // Walk through all the TestClasses, store their method
    // and initialize them with the correct ITestClass
    //
    for (ITestClass tc : m_classMap.values()) {
      fixMethodsWithClass(tc.getBeforeClassMethods(), tc, beforeClassMethods);
      fixMethodsWithClass(tc.getBeforeTestMethods(), tc, null); // HINT: does not link back to beforeTestMethods
      fixMethodsWithClass(tc.getTestMethods(), tc, testMethods);
      fixMethodsWithClass(tc.getAfterTestMethods(), tc, null);
      fixMethodsWithClass(tc.getAfterClassMethods(), tc, afterClassMethods);
      fixMethodsWithClass(tc.getBeforeSuiteMethods(), tc, beforeSuiteMethods);
      fixMethodsWithClass(tc.getAfterSuiteMethods(), tc, afterSuiteMethods);
      fixMethodsWithClass(tc.getBeforeTestConfigurationMethods(), tc, beforeXmlTestMethods);
      fixMethodsWithClass(tc.getAfterTestConfigurationMethods(), tc, afterXmlTestMethods);
      fixMethodsWithClass(tc.getBeforeGroupsMethods(), tc, m_groupMethods.getBeforeGroupsMethods());
      fixMethodsWithClass(tc.getAfterGroupsMethods(), tc, m_groupMethods.getAfterGroupsMethods());
    }
    
    m_runInfo.setTestMethods(testMethods);

    //
    // Sort the methods
    //
    m_beforeSuiteMethods = MethodHelper.collectAndOrderMethods(beforeSuiteMethods,
                                                              false,
                                                              m_runInfo,
                                                              m_annotationFinder,
                                                              true /* unique */,
                                                              m_excludedMethods);

    m_beforeXmlTestMethods = MethodHelper.collectAndOrderMethods(beforeXmlTestMethods,
                                                                false,
                                                                m_runInfo,
                                                                m_annotationFinder,
                                                                true, // CQ added by me
                                                                m_excludedMethods);

    m_beforeClassMethods = MethodHelper.collectAndOrderMethods(beforeClassMethods,
                                                              false,
                                                              m_runInfo,
                                                              m_annotationFinder,
                                                              m_excludedMethods);

    m_allTestMethods = MethodHelper.collectAndOrderMethods(testMethods,
                                                          true,
                                                          m_runInfo,
                                                          m_annotationFinder,
                                                          m_excludedMethods);

    m_afterClassMethods = MethodHelper.collectAndOrderMethods(afterClassMethods,
                                                             false,
                                                             m_runInfo,
                                                             m_annotationFinder,
                                                             m_excludedMethods);

    m_afterXmlTestMethods = MethodHelper.collectAndOrderMethods(afterXmlTestMethods,
                                                               false,
                                                               m_runInfo,
                                                               m_annotationFinder,
                                                               true, // CQ added by me
                                                               m_excludedMethods);

    m_afterSuiteMethods = MethodHelper.collectAndOrderMethods(afterSuiteMethods,
                                                             false,
                                                             m_runInfo,
                                                             m_annotationFinder,
                                                             true /* unique */,
                                                             m_excludedMethods);
  }

  private ConfigurationGroupMethods findGroupMethods(Collection<ITestClass> classes) {
    ConfigurationGroupMethods result = new ConfigurationGroupMethods(m_allTestMethods);
    
    fillGroupMethods(classes, result.getBeforeGroupsMap(), true /* before */);
    fillGroupMethods(classes, result.getAfterGroupsMap(), false /* before */);
    
    return result;
  }

  private void fillGroupMethods(Collection<ITestClass> classes, 
      Map<String, List<ITestNGMethod>> map, boolean before) 
  {
    for (ITestClass cls : classes) {
      ITestNGMethod[] methods = 
        before ? cls.getBeforeGroupsMethods() : cls.getAfterGroupsMethods();
      for (ITestNGMethod method : methods) {
        for (String group : before ? method.getBeforeGroups() : method.getAfterGroups()) {
          List<ITestNGMethod> methodList = map.get(group);
          if (methodList == null) {
            methodList = new ArrayList<ITestNGMethod>();
            map.put(group, methodList);
          }
          methodList.add(method);
        }
      }
    }
    
  }
  
  private static void ppp(String s) {
    if (true) {
      System.out.println("[TestRunner] " + s);
    }
  }

  private void fixMethodsWithClass(ITestNGMethod[] methods,
                                   ITestClass testCls,
                                   List<ITestNGMethod> methodList) {
    for (ITestNGMethod itm : methods) {
      itm.setTestClass(testCls);

      if (methodList != null) {
        methodList.add(itm);
      }
    }
  }

  public Collection<ITestClass> getIClass() {
    return m_classMap.values();
  }

  /**
   * FIXME: not used
   */
  private IClass findIClass(IClass[] classes, Class cls) {
    for (IClass c : classes) {
      if (c.getRealClass().equals(cls)) {
        return c;
      }
    }

    return null;
  }

  public String getName() {
    return m_testName;
  }

  public String[] getIncludedGroups() {
    Map<String, String> ig= m_xmlMethodSelector.getIncludedGroups();
    String[] result= (String[]) ig.values().toArray((new String[ig.size()]));

    return result;
  }

  public String[] getExcludedGroups() {
    Map<String, String> eg= m_xmlMethodSelector.getExcludedGroups();
    String[] result= (String[]) eg.values().toArray((new String[eg.size()]));

    return result;
  }

  public void setTestName(String name) {
    m_testName = name;
  }

  public void setOutputDirectory(String od) {
  if (od == null) { m_outputDirectory = null; return; } //for maven2
    File file = new File(od);
    file.mkdirs();
    m_outputDirectory= file.getAbsolutePath();
  }

  public String getOutputDirectory() {
    return m_outputDirectory;
  }

  /**
   * @return Returns the endDate.
   */
  public Date getEndDate() {
    return m_endDate;
  }

  /**
   * @return Returns the startDate.
   */
  public Date getStartDate() {
    return m_startDate;
  }

  private void addMetaGroup(String name, List<String> groupNames) {
    m_metaGroups.put(name, groupNames);
  }

  /**
   * Calculate the transitive closure of all the MetaGroups
   *
   * @param groups
   * @param unfinishedGroups
   * @param result           The transitive closure containing all the groups found
   */
  private void collectGroups(String[] groups,
                             List<String> unfinishedGroups,
                             Map<String, String> result) {
    for (String gn : groups) {
      List<String> subGroups = m_metaGroups.get(gn);
      if (null != subGroups) {

        for (String sg : subGroups) {
          if (null == result.get(sg)) {
            result.put(sg, sg);
            unfinishedGroups.add(sg);
          }
        }
      }
    }
  }

  private Map<String, String> createGroups(List<String> groups) {
    return createGroups((String[]) groups.toArray(new String[groups.size()]));
  }

  private Map<String, String> createGroups(String[] groups) {
    Map<String, String> result= new HashMap<String, String>();

    // Groups that were passed on the command line
    for (String group : groups) {
      result.put(group, group);
    }

    // See if we have any MetaGroups and
    // expand them if they match one of the groups
    // we have just been passed
    List<String> unfinishedGroups = new ArrayList<String>();

    if (m_metaGroups.size() > 0) {
      collectGroups(groups, unfinishedGroups, result);

      // Do we need to loop over unfinished groups?
      while (unfinishedGroups.size() > 0) {
        String[] uGroups = (String[]) unfinishedGroups.toArray(new String[unfinishedGroups.size()]);
        unfinishedGroups = new ArrayList<String>();
        collectGroups(uGroups, unfinishedGroups, result);
      }
    }

    //    Utils.dumpMap(result);
    return result;
  }

  /**
   * FIXME: unused
   * 
   * @param methods
   * @return All the methods that match the filtered groups.
   *         If a method belongs to an excluded group, it is automatically excluded.
   */
  public ITestNGMethod[] collectAndOrderTestMethods(ITestNGMethod[] methods) {
    return MethodHelper.collectAndOrderMethods(methods,
                                               true /* forTests */,
                                               m_runInfo,
                                               m_annotationFinder,
                                               m_excludedMethods);
  }

  /**
   * The main entry method for TestRunner.
   *
   * This is where all the hard work is done:
   * - Invoke configuration methods
   * - Invoke test methods
   * - Catch exceptions
   * - Collect results
   * - Invoke listeners
   * - etc...
   */
  public void run() {
    beforeRun();

    try {
      privateRun(getTest());
    }
    finally {
      afterRun();
    }
  }

  /** Before run preparements. */
  private void beforeRun() {
    //
    // Log the start date
    //
    m_startDate = new Date(System.currentTimeMillis());

    // Log start
    logStart();

    // Invoke listeners
    fireEvent(true /*start*/);
  }

  public void privateRun(XmlTest xmlTest) {
    Map<String, String> params = xmlTest.getParameters();

    //
    // Calculate the lists of tests that can be run in sequence and in parallel
    //
    List<ITestNGMethod> sequentialList= new ArrayList<ITestNGMethod>();
    List<ITestNGMethod> parallelList= new ArrayList<ITestNGMethod>();

    computeTestLists(sequentialList, parallelList);
    
    log(3, "Found " + (sequentialList.size() + parallelList.size()) + " applicable methods");
    
    //
    // Find out all the group methods
    //
    m_groupMethods = findGroupMethods(m_classMap.values());

    //
    // Create the workers
    //
    List<TestMethodWorker> workers = new ArrayList<TestMethodWorker>();
    
    // These two variables are used throughout the workers to keep track
    // of what beforeClass/afterClass methods have been invoked
    Map<ITestClass, ITestClass> beforeClassMethods =
      new HashMap<ITestClass, ITestClass>();
    Map<ITestClass, ITestClass> afterClassMethods =
      new HashMap<ITestClass, ITestClass>();
    
    ClassMethodMap cmm = new ClassMethodMap(m_allTestMethods);
    
    // All the sequential tests are place in one worker, guaranteeing they
    // will be invoked sequentially
    if (sequentialList.size() > 0) {
      workers.add(new TestMethodWorker(m_invoker,
                                       sequentialList.toArray(new ITestNGMethod[sequentialList
                                                              .size()]),
                                       m_xmlTest.getSuite(),
                                       params,
                                       beforeClassMethods, afterClassMethods,
                                       m_allTestMethods,
                                       m_groupMethods,
                                       cmm));
    }

    // All the parallel tests are placed in a separate worker, so then can be
    // invoked in parallel
    if (parallelList.size() > 0) {
      for (ITestNGMethod tm : parallelList) {
        workers.add(new TestMethodWorker(m_invoker,
                                         new ITestNGMethod[] { tm },
                                         m_xmlTest.getSuite(),
                                         params,
                                         beforeClassMethods, afterClassMethods,
                                         m_allTestMethods,
                                         m_groupMethods,
                                         cmm));
      }
    }

    //
    // Invoke the workers
    //
    if (xmlTest.isParallel()) {

      //
      // Parallel run
      //
      long maxTimeOut= 10 * 1000; // 1 second
      IPooledExecutor executor= ThreadUtil.createPooledExecutor(m_xmlTest.getSuite()
                                                                .getThreadCount());

      for (TestMethodWorker tmw : workers) {
        long mt= tmw.getMaxTimeOut();
        if (mt > maxTimeOut) {
          maxTimeOut= mt;
        }

        executor.execute(tmw);
      }
      try {
        executor.shutdown();
        log("Waiting for termination, timeout:" + maxTimeOut);
        executor.awaitTermination(maxTimeOut);
        log("Successful termination");
      }
      catch(InterruptedException e) {
        e.printStackTrace();
      }

    }
    else {

      //
      // Sequential run
      //
      for (TestMethodWorker tmw : workers) {
        tmw.setAllTestMethods(m_allTestMethods);
        tmw.run();
      }
    }

  }

  private void afterRun() {
    //
    // Log the end date
    //
    m_endDate = new Date(System.currentTimeMillis());
    
    if (getVerbose() >= 3) {
      dumpInvokedMethods();
    }

    // Invoke listeners
    fireEvent(false /*stop*/);

    // Statistics
//    logResults();
  }
  
  /**
   * @param regexps
   * @param group
   * @return true if the map contains at least one regexp that matches the 
   * given group
   */
  private boolean containsString(Map<String, String> regexps, String group) {
    for (String regexp : regexps.values()) {
      boolean match = Pattern.matches(regexp, group);
      if (match) {
        return true;
      }
    }
    
    return false;
  }

  /**
   * Creates the
   * @param sequentialList
   * @param parallelList
   */
  private void computeTestLists(List<ITestNGMethod> sequentialList,
                                List<ITestNGMethod> parallelList) 
  {

    Map<String, String> groupsDependedUpon= new HashMap<String, String>();
    Map<String, String> methodsDependedUpon= new HashMap<String, String>();

    for (int i= m_allTestMethods.length - 1; i >= 0; i--) {
      ITestNGMethod tm= m_allTestMethods[i];
      String[] currentGroups = tm.getGroups();
      String[] currentGroupsDependedUpon= tm.getGroupsDependedUpon();
      String[] currentMethodsDependedUpon= tm.getMethodsDependedUpon();

      String thisMethodName = 
        tm.getMethod().getDeclaringClass().getName() + "." + 
        tm.getMethod().getName();
      if (currentGroupsDependedUpon.length > 0) {
        for (String gdu : currentGroupsDependedUpon) {
          groupsDependedUpon.put(gdu, gdu);
        }

        sequentialList.add(0, tm);
      }
      else if (currentMethodsDependedUpon.length > 0) {
        for (String cmu : currentMethodsDependedUpon) {
          methodsDependedUpon.put(cmu, cmu);
        }
        sequentialList.add(0, tm);
      }
      // Is there a method that depends on the current method?
      else if (containsString(methodsDependedUpon, thisMethodName)) {
        sequentialList.add(0, tm);
      }
      else if (currentGroups.length > 0) {
        boolean isSequential= false;

        for (String group : currentGroups) {
          if (containsString(groupsDependedUpon, group)) {
            sequentialList.add(0, tm);
            isSequential = true;

            break;
          }
        }
        if (!isSequential) {
          parallelList.add(0, tm);
        }
      }
      else {
        parallelList.add(0, tm);
      }
    }
    
    // Finally, sort the parallel methods by classes

    Collections.sort(parallelList, TestNGMethod.SORT_BY_CLASS);

    if (getVerbose() >= 2) {
      log(3, "WILL BE RUN IN RANDOM ORDER:");
      for (ITestNGMethod tm : parallelList) {
        log(3, "  " + tm);
      }

      log(3, "WILL BE RUN SEQUENTIALLY:");
      for (ITestNGMethod tm : sequentialList) {
        log(3, "  " + tm);
      }
      
      log(3, "===");
    }
  }

  /**
   * TODO: not used
   */
  private void invokeClassConfigurations(ITestNGMethod[] classMethods,
                                         XmlTest xmlTest,
                                         boolean before) {
    for (IClass testClass : m_classMap.values()) {
      m_invoker.invokeConfigurations(testClass,
                                     classMethods,
                                     m_xmlTest.getSuite(),
                                     xmlTest.getParameters(),
                                     null /* instance */);

      String msg= "Marking class " + testClass + " as " + (before ? "before" : "after")
        + "ConfigurationClass =true";

      log(3, msg);
    }
  }

  /**
   * Logs the beginning of the {@link #privateRun()}.
   */
  private void logStart() {
    log(3,
        "Running test " + m_testName + " on " + m_classMap.size() + " " + " classes, "
        + " included groups:[" + mapToString(m_xmlMethodSelector.getIncludedGroups())
        + "] excluded groups:[" + mapToString(m_xmlMethodSelector.getExcludedGroups()) + "]");

    if (getVerbose() >= 3) {
      for (ITestClass tc : m_classMap.values()) {
        ((TestClass) tc).dump();
      }
    }
  }

  /**
   * Trigger the start/finish event.
   *
   * @param isStart <tt>true</tt> if the event is for start, <tt>false</tt> if the
   *                event is for finish
   */
  private void fireEvent(boolean isStart) {
    for (ITestListener itl : m_testListeners) {
      if (isStart) {
        itl.onStart(this);
      }
      else {
        itl.onFinish(this);
      }
    }
  }

  /////
  // ITestResultNotifier
  //

  public void addPassedTest(ITestNGMethod tm, ITestResult tr) {
    synchronized(m_passedTests) {
      m_passedTests.addResult(tr, tm);
    }
  }

  public Set<ITestResult> getPassedTests(ITestNGMethod tm) {
    return m_passedTests.getResults(tm);
  }

  public void addSkippedTest(ITestNGMethod tm, ITestResult tr) {
    synchronized(m_skippedTests) {
      m_skippedTests.addResult(tr, tm);
    }
  }

  public void addInvokedMethod(InvokedMethod im) {
    synchronized(m_invokedMethods) {
      m_invokedMethods.add(im);
    }
  }

  public void addFailedTest(ITestNGMethod testMethod, ITestResult result) {
    logFailedTest(testMethod, result, false /* withinSuccessPercentage */);
  }

  public void addFailedButWithinSuccessPercentageTest(ITestNGMethod testMethod,
                                                      ITestResult result) {
    logFailedTest(testMethod, result, true /* withinSuccessPercentage */);
  }

  public XmlTest getTest() {
    return m_xmlTest;
  }

  public List<ITestListener> getTestListeners() {
    return m_testListeners;
  }

  //
  // ITestResultNotifier
  /////

  /**
   * FIXME: not used
   * 
   * @param declaringClass
   * @return
   */
  private IClass findTestClass(Class<?> declaringClass) {
    IClass result= m_classMap.get(declaringClass);
    if (null == result) {
      for (Class cls : m_classMap.keySet()) {
        if (declaringClass.isAssignableFrom(cls)) {
          result= m_classMap.get(cls);
          assert null != result : "Should never happen";
        }
      }
    }

    return result;
  }

  public ITestNGMethod[] getTestMethods() {
    return m_allTestMethods;
  }

  private void logFailedTest(ITestNGMethod method,
                             ITestResult tr,
                             boolean withinSuccessPercentage) {
    if (withinSuccessPercentage) {
      synchronized(m_failedButWithinSuccessPercentageTests) {
        m_failedButWithinSuccessPercentageTests.addResult(tr, method);
      }
    }
    else {
      synchronized(m_failedTests) {
        m_failedTests.addResult(tr, method);
      }
    }
  }

  private String mapToString(Map m) {
    StringBuffer result= new StringBuffer();
    for (Object o : m.values()) {
      result.append(o.toString()).append(" ");
    }

    return result.toString();
  }

  private void log(int level, String s) {
    Utils.log("TestRunner", level, s);
  }

  public static int getVerbose() {
    return m_verbose;
  }

  public void setVerbose(int n) {
    m_verbose = n;
  }

  private void log(String s) {
    Utils.log("TestRunner", 2, s);
  }

  public IResultMap getPassedTests() {
    return m_passedTests;
  }

  public IResultMap getSkippedTests() {
    return m_skippedTests;
  }

  public IResultMap getFailedTests() {
    return m_failedTests;
  }

  public IResultMap getFailedButWithinSuccessPercentageTests() {
    return m_failedButWithinSuccessPercentageTests;
  }

  /////
  // Listeners
  //

  public void addTestListener(ITestListener il) {
    m_testListeners.add(il);
  }

  //
  // Listeners
  /////

  /**
   * @return Returns the suite.
   */
  public ISuite getSuite() {
    return m_suite;
  }

  private List<InvokedMethod> m_invokedMethods = new ArrayList<InvokedMethod>();

  public ITestNGMethod[] getAllTestMethods() {
    return m_allTestMethods;
  }

  private void dumpInvokedMethods() {
    System.out.println("\n*********** INVOKED METHODS\n");
    for (InvokedMethod im : m_invokedMethods) {
      if (im.isTestMethod()) {
        System.out.print("\t\t");
      }
      else if (im.isConfigurationMethod()) {
        System.out.print("\t");
      }
      else {
        continue;
      }
      System.out.println("" + im);
    }
    System.out.println("\n***********\n");
  }

  /**
   * @return
   */
  public List<ITestNGMethod> getInvokedMethods() {
    List<ITestNGMethod> result= new ArrayList<ITestNGMethod>();
    for (InvokedMethod im : m_invokedMethods) {
      ITestNGMethod tm= im.getTestMethod();
      tm.setDate(im.getDate());
      result.add(tm);
    }

    return result;
  }
  
  public String getHost() {
    return m_host;
  }

  public Collection<ITestNGMethod> getExcludedMethods() {
    Map<ITestNGMethod, ITestNGMethod> vResult = 
      new HashMap<ITestNGMethod, ITestNGMethod>();
    
    for (ITestNGMethod m : m_excludedMethods) {
      vResult.put(m, m);
    }
    
    return vResult.keySet();
  }
  
} // TestRunner
