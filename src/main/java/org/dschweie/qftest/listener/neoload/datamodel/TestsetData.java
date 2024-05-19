package org.dschweie.qftest.listener.neoload.datamodel;

import java.util.List;

import com.neotys.rest.dataexchange.model.Entry;
import com.neotys.rest.dataexchange.model.EntryBuilder;
import com.neotys.rest.dataexchange.model.Status;

import de.qfs.apps.qftest.extensions.qftest.TestRunEvent;

public class TestsetData extends AbstractExternalData
{

  public TestsetData(String name)
  {
    super("QF-Test.Testset.".concat(name));
  }

  @Override
  public List<Entry> getEntries(TestRunEvent event, Status status, double dRealtime, double dDuration)
  {
    List<Entry> retval = super.getEntries(event, status, dRealtime, dDuration);
    retval.add((new EntryBuilder(this.getMetricsPath(EXCEPTIONS), event.getTimestamp())).unit("exceptions").value(Double.valueOf(this.getExceptions())).url("").status(status).build());
    retval.add((new EntryBuilder(this.getMetricsPath(ERRORS), event.getTimestamp())).unit("errors").value(Double.valueOf(this.getErrors())).url("").status(status).build());
    retval.add((new EntryBuilder(this.getMetricsPath(WARNINGS), event.getTimestamp())).unit("warnings").value(Double.valueOf(this.getWarnings())).url("").status(status).build());
    return retval;
  }

}
