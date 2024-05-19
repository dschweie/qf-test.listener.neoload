package org.dschweie.qftest.listener.neoload.datamodel;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import com.neotys.rest.dataexchange.model.Entry;
import com.neotys.rest.dataexchange.model.EntryBuilder;
import com.neotys.rest.dataexchange.model.Status;

import de.qfs.apps.qftest.extensions.qftest.TestRunEvent;

public abstract class AbstractExternalData
{
  public static final int DURATION    = 0;
  public static final int REALTIME    = 1;
  public static final int STATE       = 2;
  public static final int EXCEPTIONS  = 3;
  public static final int ERRORS      = 4;
  public static final int WARNINGS    = 5;

  protected List<String> listExternalDataPath = null;

  private int iErrors;
  private int iExceptions;
  private int iWarnings;

  public AbstractExternalData(String name)
  {
    this(Arrays.asList(name.split("\\x2e")));
  }

  public AbstractExternalData(List<String> list)
  {
    this.listExternalDataPath = new Vector<>();
    for(int i=0; i < list.size(); ++i)
      this.listExternalDataPath.add(list.get(i));
    this.resetAllSignals();
  }

  public List<com.neotys.rest.dataexchange.model.Entry> getEntries(TestRunEvent event, Status status, double dRealtime, double dDuration )
  {
    List<Entry> listEntries = new Vector<>();

    listEntries.add((new EntryBuilder(this.getMetricsPath(DURATION), event.getTimestamp())).unit("s").value(Double.valueOf(dDuration)).url("").status(status).build());
    listEntries.add((new EntryBuilder(this.getMetricsPath(REALTIME), event.getTimestamp())).unit("s").value(Double.valueOf(dRealtime)).url("").status(status).build());
    listEntries.add((new EntryBuilder(this.getMetricsPath(STATE), event.getTimestamp())).unit("code").value(Double.valueOf(event.getLocalState())).url("").status(status).build());

    return listEntries;
  }

  protected List<String> getMetricsPath(int metricId)
  {
    List<String> path = new Vector<>(this.listExternalDataPath);
    switch(metricId)
    {
      case DURATION   : path.add("Duration"); break;
      case REALTIME   : path.add("Realtime"); break;
      case STATE      : path.add("Result"); path.add("State"); break;
      case EXCEPTIONS : path.add("Result"); path.add("Exceptions"); break;
      case ERRORS     : path.add("Result"); path.add("Errors"); break;
      case WARNINGS   : path.add("Result"); path.add("Warnings"); break;
      default         : path.add("Unknown"); break;
    }

    return path;
  }

  protected List<String> getDataPath()
  {
    return this.listExternalDataPath;
  }

  public void signalError()
  {
    this.iErrors += 1;
  }

  public void resetErrors()
  {
    this.iErrors = 0;
  }

  public void signalException()
  {
    this.iExceptions += 1;
  }

  public void resetExceptions()
  {
    this.iExceptions = 0;
  }

  public void signalWarning()
  {
    this.iWarnings += 1;
  }

  public void resetWarnings()
  {
    this.iWarnings = 0;
  }

  public void resetAllSignals()
  {
    this.resetWarnings();
    this.resetErrors();
    this.resetExceptions();
  }

  public int getErrors()
  {
    return iErrors;
  }

  public int getExceptions()
  {
    return iExceptions;
  }

  public int getWarnings()
  {
    return iWarnings;
  }
}
