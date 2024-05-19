package org.dschweie.qftest.listener.neoload.datamodel;

import de.qfs.apps.qftest.extensions.qftest.TestRunEvent;
import de.qfs.apps.qftest.extensions.qftest.TestSuiteNode;

public class SequenceData extends TestcaseData
{
  public SequenceData(String name)
  {
    super(name);
  }

  public SequenceData(TestRunEvent event)
  {
    super();
    TestSuiteNode[] axNodes = event.getPath();
    try
    {
      for (TestSuiteNode axNode : axNodes)
        if(axNode.getType().matches("(TestCase)|(TestSet)|(ProcedureCall)"))
          this.listExternalDataPath.add(axNode.getName());
        else
          this.listExternalDataPath.add(axNode.getTreeName());
    } catch (Exception e)
    {
      // Nur zur Abwehr
    }
  }

}
