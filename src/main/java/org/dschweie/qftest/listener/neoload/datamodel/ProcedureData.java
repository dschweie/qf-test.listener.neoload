package org.dschweie.qftest.listener.neoload.datamodel;

import java.util.List;

import com.neotys.rest.dataexchange.model.Entry;
import com.neotys.rest.dataexchange.model.Status;

import de.qfs.apps.qftest.extensions.qftest.TestRunEvent;

public class ProcedureData extends AbstractExternalData
{

  public ProcedureData(String name)
  {
    super("QF-Test.Procedure.".concat(name));
  }

  @Override
  public List<Entry> getEntries(TestRunEvent event, Status status, double dRealtime, double dDuration)
  {
    return super.getEntries(event, status, dRealtime, dDuration);
  }

}
