package org.dschweie.qftest.listener.neoload;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;

import org.dschweie.qftest.listener.neoload.datamodel.AbstractExternalData;
import org.dschweie.qftest.listener.neoload.datamodel.ProcedureData;
import org.dschweie.qftest.listener.neoload.datamodel.SequenceData;
import org.dschweie.qftest.listener.neoload.datamodel.TestcaseData;
import org.dschweie.qftest.listener.neoload.datamodel.TestsetData;
import com.neotys.rest.dataexchange.client.DataExchangeAPIClient;
import com.neotys.rest.dataexchange.client.DataExchangeAPIClientFactory;
import com.neotys.rest.dataexchange.model.ContextBuilder;
import com.neotys.rest.dataexchange.model.EntryBuilder;
import com.neotys.rest.dataexchange.model.Status;
import com.neotys.rest.dataexchange.model.Status.State;
import com.neotys.rest.error.NeotysAPIException;

import de.qfs.apps.qftest.extensions.qftest.AbstractTestRunListener;
import de.qfs.apps.qftest.extensions.qftest.TestRunEvent;
import de.qfs.apps.qftest.run.AbstractRunContext;
import de.qfs.apps.qftest.shared.exceptions.TestException;
import de.qfs.apps.qftest.step.AbstractStep;

/*
 * Diese Klasse ist vom TestrunListener von QF-Test abgeleitet.
 * Die Methoden werden von QF-Test aktiviert.
 *
 * \msc "Sequence-Diagramm Kommunikation QFTest mit Neoload"

  Neoload,"QF-Test";
  Neoload box Neoload [label = "Init"];
  Neoload->"QF-Test" [ label = "QF-Test im Daemonmodus starten" ];
  "QF-Test"=>"QF-Test" [ label = "QF-Test-Suite starten"];
  "QF-Test"=>"QF-Test" [ label = "TestrunListener registrieren"];
  Neoload box Neoload [label="Actions"];
  Neoload->"QF-Test" [ label = "Testfall(1) starten" ];
  "QF-Test"=>"QF-Test" [ label = "Testfall(1) ausfuehren"];
  "QF-Test"=>Neoload [ label = "Messwerte nach Neoload senden"];
  ...;
  Neoload->"QF-Test" [ label = "Testfall(n) starten" ] ;
  "QF-Test"=>"QF-Test" [ label = "Testfall(n) ausfuehren"];
  "QF-Test"=>Neoload [ label = "Messwerte nach Neoload senden"];
  Neoload box Neoload [label="End"];
  Neoload->"QF-Test" [ label = "Daemonmodus beenden" ];
  "QF-Test"=>"QF-Test" [ label = "QF-Test-Suite beenden"];

 * \endmsc

 * \msc "Sequence-Diagramm Kommunikation QFTest mit TestrunListener"

  "Testfall","TestrunListener";
  "Testfall" box "Testfall" [label = "Eintritt in den Testfall"];
  "Testfall" -> "TestrunListener" [label = "Methode nodeEntered aktivieren"];
  "TestrunListener" box "TestrunListener" [label = "Methode nodeEntered:
  \nEingangswerte merken:
  \nZeitstempel bei Knoteneintritt
  \ncountExceptions bei Knoteneintritt
  \n..."];
  ...;
  "Testfall" box "Testfall" [label = "Aktionen innerhalb des Testfalls"];
  ...;
  "Testfall" box "Testfall" [label = "Aktionen mit Fehler ( Error oder Excception"];
  "Testfall" -> "TestrunListener" [label = "Methode problemOccurred aktivieren"];
  "TestrunListener" box "TestrunListener" [label = "Methode problemOccurred:\nFehlermeldung an Neoload senden"];
  ...;
  "Testfall" box "Testfall" [label = "Weitere Aktionen innerhalb des Testfalls"];
  ...;
  "Testfall" box "Testfall" [label = "Austritt aus dem Testfall"];
  "Testfall" -> "TestrunListener" [label = "Methode nodeExited aktivieren"];
  "TestrunListener" box "TestrunListener" [label = "Methode nodeExited:
  \nDie Messwerte werden aus der Differenz von Einangs - und  Ausgangswerten bestimmt.
  \nduration = Zeitstempel bei Knoteneintritt - Zeitstempel bei Knotenaustritt
  \n
  \nBerechnung von Statistikwerte
  \n
  \nMesswerte und Statisken an Neoload senden"];

 * \endmsc
 *
 *
 */

/**
 *  \brief      Die Klasse dient der übermittlung von Messwerten von QF-Test an NeoLoad.
 *
 *  Die Testwerkzeuge NeoLoad und QF-Test werden zur Verfolgung von
 *  unterschiedlichen Testzielen eingesetzt. Mit NeoLoad lassen sich
 *  Performanztests durchführen und QF-Test wird überwiegend für funktionale
 *  Tests verwendet.
 *
 *  Wenn in einem Performanztest die Zeit gemessen werden soll, die vergeht,
 *  bis der Anwender Daten in seiner Applikation tatsächlich sehen kann, ist
 *  ein Testausführungswerkzeug, wie QF-Test, klar im Vorteil. NeoLoad bietet
 *  für ein solches Szenario eine Schnittstelle an, mit der andere
 *  Testwerkzeuge gestartet werden können und diese dann Messwerte an NeoLoad
 *  übertragen können.
 *
 *  Diese Klasse ist eine Spezialisierung des AbstractTestRunListener, der
 *  über das DataExchangeAPI Messwerte von QF-Test in Richtung NeoLoad
 *  überträgt, damit zur Testauswertung nicht QF-Test-Protokolle
 *  ausgewertet werden müssen.
 *
 *  \xrefitem   opqfs "Offener Punkt (QFS)" "Offene Punkte für QFS"
 *              Während der Implementierung hat sich herausgestellt, dass der
 *              Ausdruck <br/>
 *              <code>this.rc.getCurrentLog().getDuration()</code><br/>
 *              nicht genutzt werden kann, die Methode getDuration() vom
 *              Compiler nicht aufgelöst werden kann.<br/>
 *              Aus diesem Grund wurde diese Klasse als abstrakte Klasse
 *              definiert. In der zugehörigen QF-Testsuite findet sich
 *              eine Spezialisierung dieser Klasse, in der die Methoden<br/>
 *              &nbsp;&nbsp;&nbsp;&bull; AbstractNeoloadDataExchangeListener.getDurationInS()<br/>
 *              &nbsp;&nbsp;&nbsp;&bull; AbstractNeoloadDataExchangeListener.getRealtimeInS()<br/>
 *              implementiert sind.
 *
 */
public abstract class AbstractNeoloadDataExchangeListener  extends AbstractTestRunListener
{
  /**
   *  \brief    Klassenattribut mit regulärem Ausdruck, der zur Identifikation der relevanten Schritte verwendet wird.
   *
   *  Ein QF-Test-Skript besteht aus unterschiedlichen Schritten. Im Grunde
   *  jedes Element in der Baumstruktur in QF-Test ist ein Schritt. Der
   *  Listener agiert auf Testfällen, Testfallsätzen, Sequenzen und Prozeduren.
   */
  public static  final String REGEX_OBSERVEDNODES =  "(Test((Set)|(Case)|(Step)))|(ProcedureCall)|(((Basic)|(TimeConstrained))Sequence)";

  /**
   *  \brief    In dem Attribut wird der RunContext von QF-Test gehalten.
   *
   *  Der RunContext ist einer Klasse, die QF-Test bereitstellt, um in Skripten
   *  auf Informationen während der Testdurchführung zu gewinnen. Diese Klasse
   *  benötigt eine Referenz auf die Instanz des RunContext um auf Variablen
   *  des Frameworks zugreifen zu können. Dazu gehören zum Beispiel die
   *  Zähler der Schleifen, die für den aktuellen Testfall und Testschritt
   *  stehen.
   */
  protected AbstractRunContext rc;

  /**
   *  \brief    In dem Attribut wird die URL zu dem Dienst von NeoLoad gehalten.
   *
   *  Das DataExchangeAPI ist ein Webservice von NeoLoad, der nur dann zur
   *  Verfügung steht, wenn ein Test in NeoLoad gestartet ist. Die URL wird
   *  zur Kommunikation mit der entsprechenden Instanz benötigt.
   */
  protected URL xUrl = null;

  /**
   *  \brief    In dem Attribut wird der NeoLoad Client gehalten, der mit dem Webservice kommuniziert.
   *
   *  Die Firma Neotys bietet für das DataExchangeAPI einen Java-Client an, der
   *  die Kommunikation mit dem Webservice übernimmt. Der Nutzer des Client
   *  übergibt lediglich Instanzen mit den entsprechenden Parametern.
   */
  private DataExchangeAPIClient xClient = null;

  /**
   *  \brief    Beschreibung für die Art der Hardware zu Statistikzwecken.
   *
   *  Wenn ein Messwert an NeoLoad übertragen wird, können Informationen zur
   *  Plattform übertragen werden, nach denen gefiltert werden kann.
   *
   *  Als Hardware wird konstant der Wert "Workstation" übertragen.
   */
  private String strHardware = "Workstation";

  /**
   *  \brief    InstanzId, die NeoLoad zu einer Datenübergabe hinterlegen kann.
   *
   *  Die InstanzId ist Bestandteil des Kontext, den NeoLoad zu einem Datum
   *  speichert.
   *
   *  Aus QF-Test wird der Wert genommen, der in <code>runid</code> gespeichert
   *  ist. Der Wert setzt sich aus Datum und Uhrzeit zusammen.
   */
  private String strInstanceId = "unbekannt";

  /**
   *  \brief    Beschreibung des Ortes der Ausführung zu Statistikzwecken.
   *
   *  NeoLoad erlaubt zu jedem Datum die Angabe des sog. Kontext, über den
   *  Testergebnisse gefiltert werden können.
   */
  private String strLocation = "";

  /**
   *  \brief    Information des Betriebsystems zu Statistikzwecken.
   *
   *  NeoLoad hinterlegt im Kontext eines jeden Wertes Informationen zur
   *  Plattform. Das Betriebssystem ist ein Teil dieser Information.
   */
  private String strOS = System.getProperty("os.name", "unknown");

  /**
   *  \brief    Information auf die Software, aus der das externe Datum stammt.
   *
   *  Im Kontext eines Datums ist bei NeoLoad ein Feld für die Client Software
   *  vorgesehen.
   *
   *  Der Wert <code>QF-Test</code> ist nur ein Defaultwert, damit er nicht
   *  leer ist. Wenn QF-Test ordentlich gestartet ist, wird in diesem
   *  Attribut noch die Versionsnummer von QF-Test erg�nzt.
   */
  private String strSoftware = "QF-Test";

  /**
   *  \brief    Interne Struktur zur Verwaltung der Objekte, zu denen Messwerte übertragen werden sollen.
   *
   *  Der Listener wird über jede Art von Schritt, die z.B. gestartet oder
   *  beendet wird, informiert. Da der Listener aber nicht alle Schritte
   *  von Interesse sind, werden die interessanten Schritte in diese
   *  HashMap gehalten, um später schneller entscheiden zu können, ob
   *  der Listener tätig werden soll.
   */
  protected HashMap<String,AbstractExternalData> mapQualifiedNames;

  /**
   *  \brief    Instanzvariable, durch die definiert wird, zu welcher Art von Knoten Messwerte übertragen werden sollen.
   *
   *  Der Listener überträgt zu bestimmten Arten von Knoten Messwerte.
   *
   *  Mit dieser Instanzvariablen kann jeder Instanz des Listeners eine
   *  individuelle Konfiguration übergeben werden.
   *
   *  Wenn kein Wert explizit gesetzt wird, dann gilt der Standardwert,
   *  der über die Instanzvariable REGEX_OBSERVEDNODES definiert ist.
   */
  private String regexObservedNodes;

  /**
   *  \brief    Standardkonstruktor der Klasse
   *
   *  Dieser Konstruktor nimmt die notwendigen Initialisierungen vor.
   *
   *  \param    rc                      In dem Parameter wird die Instanz des
   *                                    RunContext übergeben. Dieser wird von
   *                                    der Klasse benötigt, um Informationen
   *                                    im Context der Testdurchführung
   *                                    ermitteln zu können.
   *
   *  \param    neoloadHost             In dem Parameter wird die Angabe des
   *                                    Host erwartet, auf dem der NeoLoad
   *                                    Controller läuft, da dort auch der
   *                                    Webservice erreichbar ist.
   *
   *  \param    neoloadPort             In dem Parameter ist der Port des
   *                                    Webservice zu übergeben.
   */
  public AbstractNeoloadDataExchangeListener(AbstractRunContext rc, String neoloadHost, int neoloadPort)
  {
    this.rc = rc;
    this.mapQualifiedNames = new HashMap<>();
    this.regexObservedNodes = AbstractNeoloadDataExchangeListener.REGEX_OBSERVEDNODES.toString();
    if(null != this.rc)
    { //  Umgebungsparameter für den Context werden gesetzt
      try
      {
        this.strInstanceId = this.rc.callProcedure("com.muthpartners.common.environment.getInstanceId");
        this.strLocation =  this.rc.lookup("env", "computername", false);
        this.strOS = System.getProperty("os.name", "unknown");
        this.strSoftware = "QF-Test ".concat(rc.lookup("qftest", "version", false));
      }
      catch (TestException e)
      {
        this.rc.logError("AbstractNeoloadDataExchangeListener.AbstractNeoloadDataExchangeListener(AbstractRunContext rc, String neoloadHost, int neoloadPort) =[0]=> ".concat(e.toString()));
      }
    }
    if(null != neoloadHost)
    {
      try
      {
        this.xUrl = new URL("http", neoloadHost, neoloadPort, "/DataExchange/v1/Service.svc/");
      }
      catch (MalformedURLException e)
      {
        if(null != this.rc)
          this.rc.logError("AbstractNeoloadDataExchangeListener.AbstractNeoloadDataExchangeListener(AbstractRunContext rc, String neoloadHost, int neoloadPort) =[1]=> ".concat(e.toString()));
        this.xUrl = null;
      }
      catch(Exception f)
      {
        if(null != this.rc)
          this.rc.logError("AbstractNeoloadDataExchangeListener.AbstractNeoloadDataExchangeListener(AbstractRunContext rc, String neoloadHost, int neoloadPort) =[2]=> ".concat(f.toString()));
      }
    }
  }

  /**
   *  \brief    Getter-Methode, die püft, ob der Listener die Voraussetzungen zur Kommunikation mit NeoLoad erfüllt.
   *
   *  Diese Methode dient der Prüfung, ob alle Informationen vorliegen, um
   *  Messwerte senden zu können.
   *
   *  @return   Die Methode liefert den Wert <code>true</code>, wenn die
   *            Bedingungen erfüllt sind.<br/>
   *            Andernfalls liefert die Methode den Wert <code>false</code>.
   */
  public boolean isReady()
  {
    return ((null!=this.xUrl) && (null!=this.getClient()));
  }

  /**
   *  \brief    Getter-Methode für die Instanz, über die mit NeoLoad kommuniziert wird.
   *
   *  Die Getter-Methode liefert die Instanz, die benötigt wird, um die
   *  Messwerte and den NeoLoad Controller zu übermitteln.
   *
   *  Die Instanzierung erfolgt durch den Getter bei Bedarf. Von daher
   *  sollte immer die Getter-Methode verwendet werden.
   *
   *  @return   Die Methode liefert im Rückgabewert eine Instanz der
   *            Klasse DataExchangeAPIClientImpl. <br/>
   *            Im Fehlerfall wird der Wert <code>null</code> geliefert.
   *            Dieser Wert ist ein Indiz dafür, dass die Klasse nicht
   *            instanziert werden konnte.
   */
  protected DataExchangeAPIClient getClient()
  {
    final ContextBuilder cb = new ContextBuilder();

    if(null == this.xClient)
    {
      try
      {
        String strScript = "";

        try
        {
          strScript = this.rc.lookup("NEOLOAD-USERPATH", false);
        }
        catch (Exception e)
        {
          strScript = this.rc.getCurrentTestCase().getQualifiedName(false);
        }

        cb.hardware(this.strHardware.toString())
          .instanceId(this.strInstanceId.toString())
          .location(this.strLocation.toString())
          .os(this.strOS.toString())
          .script(strScript.toString())
          .software(this.strSoftware.toString());

        this.xClient = DataExchangeAPIClientFactory.newClient(this.xUrl.toString() , cb.build() ,"" );
      }
      catch (NeotysAPIException e)
      {
        if(null != this.rc)
          this.rc.logError("AbstractNeoloadDataExchangeListener.getClient() =[0]=> ".concat(e.toString()).concat("\nthis.xUrl: ").concat(null==this.xUrl?"NULL":this.xUrl.toString()));
        this.xClient = null;
      }
      catch(NullPointerException n)
      {
        //  Der Block fängt NullPointerExceptions ab, die in der Vorbereitung vorkommen können.
      }
      catch(Exception f)
      {
        if(null != this.rc)
           this.rc.logError("AbstractNeoloadDataExchangeListener.getClient() =[1]=> ".concat(f.toString()));
      }
   }

    return this.xClient;
  }

  /**
   *  \brief    Setter-Methode für das Instantattribut strInstanceId
   *
   *  Diese Methode ist Setter-Methode für das Instantattribut
   *  AbstractNeoloadDataExchangeListener.strInstanceId .
   *
   *  @param    id            In dem Parameter kann eind Wert übergeben werden,
   *                          der fortan in der Kommunikation mit dem Controller
   *                          verwendet wird.
   *
   *  \sa       AbstractNeoloadDataExchangeListener.strInstanceId
   */
  protected void setInstanceId(String id)
  {
    if(!this.strInstanceId.equalsIgnoreCase(id))
    {
      this.strInstanceId = id;
      this.xClient = null;
    }
  }

  /**
   *  \brief    Setter-Methode für den regulären Ausdruck, der festlegt, welche Knoten überwacht werden sollen.
   *
   *  Mit dieser Methode kann von Aufrufer festgelegt werden, welche von welchen
   *  Knotentypen in QF-Test Messwerte übermittelt werden sollen.
   *
   *  \note     Diese Methode sollte nach Möglichkeit vor dem Start eines
   *            Tests gesetzt werden, da der Aufruf sofort Einfluss auf das
   *            Verhalten des Listeners hat.<br/>
   *            Ein Aufruf während eines laufenden Tests von QF-Test hat zur
   *            Folge, dass von dem Zeitpunkt an die Einstellungen aktiv sind.<br/>
   *            Der Ausdruck kann nicht rückwirkend angewendet werden.
   *
   *  @param    observedNodes In dem Parameter ist ein regulärer Ausdruck zu
   *                          übergeben, mit dem dann Prüfungen gemacht werden,
   *                          um zu entscheiden, ob ein Knoten im Monitoring
   *                          auftauchen soll oder nicht.
   *
   *  \sa       AbstractNeoloadDataExchangeListener.regexObservedNodes
   */
  public void setObservedNodeRegEx(String observedNodes)
  {
    this.regexObservedNodes = observedNodes.toString();
  }

  /**
   *  \brief    Getter-Methode für den regulären Ausdruck, der festlegt, welche Knoten überwacht werden sollen.
   *
   *  Getter-Methode, die den regulären Ausdruck liefert, der zur Prüfung
   *  herangezogen wird, um zu entscheiden, ob der Knoten relevant ist.
   *
   *  @return   Die Methode liefert den aktuellen regulären Ausdruck zurück,
   *            der in der Instanz Anwendung findet.
   *
   *  \sa       AbstractNeoloadDataExchangeListener.regexObservedNodes
   *  \sa       AbstractNeoloadDataExchangeListener.REGEX_OBSERVEDNODES
   */
  public String getObservedNodeRegEx()
  {
    return this.regexObservedNodes.toString();
  }

  /**
   *  \brief    Die Methode setzt zu Beginn eines neuen Testlauf die InstanceId
   *
   *  Diese Methode wird von QF-Test immer dann aufgerufen, wenn ein neuer
   *  Testlauf gestartet wird.
   *
   *  Wenn diese Methode aufgrufen wird, dann wird nach dem aktuellen Wert von
   *  <code>${qftest:runid}</code> gefragt, damit diese dann als InstanceId
   *  verwendet werden kann.
   *
   *  \param    event         In dem Parameter übergibt QF-Test die
   *                          Informationen über den Knoten, der betreten
   *                          wird und Hinweise zum aktuellen Status der
   *                          Testausführung.
   *
   *  \sa       AbstractNeoloadDataExchangeListener.setInstanceId(String)
   */
  @Override
  public void runStarted(TestRunEvent event)
  {
    try
    {
      this.setInstanceId(this.rc.lookup("qftest", "runid", false));
    } catch (TestException e)
    {
      if(null != this.rc)
        this.rc.logError("AbstractNeoloadDataExchangeListener.runStarted(TestRunEvent event) =[0]=> ".concat(e.toString()));
    }
    catch(NullPointerException n)
    {
      //  Der Block fängt NullPointerExceptions ab, die in der Vorbereitung vorkommen können.
    }
    catch(Exception f)
    {
      if(null != this.rc)
        this.rc.logError("AbstractNeoloadDataExchangeListener.runStarted(TestRunEvent event) =[1]=> ".concat(f.toString()));
    }
  }

  /**
   *  \brief    Die Methode nimmt den Knoten in die Überwachung auf, falls er den Anforderungen entspricht.
   *
   *  Diese Methode wird von QF-Test immer dann aufgerufen, wenn ein Knoten
   *  betreten wird.
   *
   *  Der Listener prüft den neuen Knoten auf Relevanz für die Ermittlung von
   *  Messwerten. Wenn der Knoten in die überwachung aufzunehmen ist, dann
   *  wird der Knoten in die Liste AbstractNeoloadDataExchangeListener.mapQualifiedNames
   *  aufgenommen.
   *
   *  \param    event         In dem Parameter übergibt QF-Test die
   *                          Informationen über den Knoten, der betreten
   *                          wird und Hinweise zum aktuellen Status der
   *                          Testausführung.
   *
   *  \sa       AbstractNeoloadDataExchangeListener.mapQualifiedNames
   */
  @Override
  public void nodeEntered(TestRunEvent event)
  {
    if(     ( event.getNode().getType().matches( this.getObservedNodeRegEx() ) )
        &&  ( !this.mapQualifiedNames.containsKey( event.getNode().getId() )   )  )
    { //  In diesem Fall ist die ID zu erfassen
      switch(event.getNode().getType())
      {
        case "TestCase"       : this.mapQualifiedNames.put( event.getNode().getId(),
                                                            new TestcaseData(this.rc.getCurrentTestCase().getQualifiedName(false))
                                                          );
                                break;
        case "TestSet"        : this.mapQualifiedNames.put( event.getNode().getId(),
                                                            new TestsetData(this.rc.getCurrentTestSet().getQualifiedName(false))
                                                          );
                                break;
        case "ProcedureCall"  : this.mapQualifiedNames.put( event.getNode().getId(),
                                                            new ProcedureData(event.getNode().getName())
                                                          );
                                                break;
        default               : this.mapQualifiedNames.put( event.getNode().getId(),
                                                            new SequenceData(event)
                                                          );
                                break;
      }
    }
  }

  /**
   *  \brief    Diese Methode wird von QF-Test aufgerufen, wenn es zu einem Fehlerfall im Ablauf kommt.
   *
   *  über diese Methode werden Listner von QF-Test über einen Fehler im
   *  Testablauf informiert.
   *
   *  Diese Methode arbeitet konkret zwei Aufgaben ab:
   *  \li   Inkrementieren des Fehlerzählers in den übergeordneten Knoten<br/>
   *        Die übergeordneten Knoten, wie zum Beispiel Testfallknoten messen
   *        neben der Dauer auch die Anzahl der übergebenen Fehler. Diese
   *        Information wird in den betroffnen Knoten aktualisiert.
   *  \li   übergabe eines Fehlerobjekts an die Schnittstelle von NeoLoad<br/>
   *        Das Auftreten eines Fehlers wird auch an NeoLoad in Form eines
   *        Fehlerobjekts gemeldet, welches dann in den Errors auch auftaucht.
   *
   *  \param    event         In diesem Parameter übergibt QF-Test eine Instanz
   *                          mit Informationen zur Art und Kritikalität des
   *                          gemeldeten Ereignis. Zu den Informationen gehört
   *                          auch der Kontext, in dem das Ereignis aufgetreten
   *                          ist.
   */
  @Override
  public void problemOccurred(TestRunEvent event)
  {
    String astrTextCode[] = { "QF-Test.OK",
                              "QF-Test.WARNING",
                              "QF-Test.ERROR",
                              "QF-Test.EXCEPTION",
                              "QF-Test.SKIPPED",
                              "QF-Test.NOT_IMPLEMENTED" };
    try
    { //  Ereignis wird in den Testfällen und Testfallsätzen festgehalten
      AbstractStep step = this.rc.getCurrentTestCase();
      while( null != step )
      {
        if(this.mapQualifiedNames.containsKey(step.getId()))
        {
          AbstractExternalData data = this.mapQualifiedNames.get(step.getId());
          switch(event.getState())
          {
            case TestRunEvent.STATE_ERROR     : data.signalError(); break;
            case TestRunEvent.STATE_EXCEPTION : data.signalException(); break;
            case TestRunEvent.STATE_WARNING   : data.signalWarning(); break;
          }
        }
        step = step.getParent();
      }


      //  Ereignis wird an DataExchangeAPI kommuniziert
      Status xStatus = (new com.neotys.rest.dataexchange.model.StatusBuilder()).code(astrTextCode[event.getState()]).message(event.getMessage()).state(State.FAIL).build();


      switch(event.getState())
      {
        case TestRunEvent.STATE_EXCEPTION : this.getClient().addEntry((new EntryBuilder(  Arrays.asList(  "QF-Test/Incidents/"
                                                                                                          .concat("Exception").concat("/")
                                                                                                          .concat(this.rc.getCurrentTestCase().getName()).concat("/")
                                                                                                          .concat(event.getNode().getTreeName()).split("/") ),
                                                                                          event.getTimestamp()  )  ).unit("exception")
                                                                                                                    .value(Double.valueOf(1.0))
                                                                                                                    .url("")
                                                                                                                    .status(xStatus)
                                                                                                                    .build());
                                            break;
        case TestRunEvent.STATE_ERROR     : this.getClient().addEntry((new EntryBuilder(  Arrays.asList(  "QF-Test/Incidents/"
                                                                                                          .concat("Error").concat("/")
                                                                                                          .concat(this.rc.getCurrentTestCase().getName()).concat("/")
                                                                                                          .concat(event.getNode().getTreeName()).split("/") ),
                                                                                          event.getTimestamp()  )  ).unit("error")
                                                                                                                    .value(Double.valueOf(1.0))
                                                                                                                    .url("")
                                                                                                                    .status(xStatus)
                                                                                                                    .build()  );
                                            break;
        case TestRunEvent.STATE_WARNING   : /*
                                            this.getClient().addEntry((new EntryBuilder(  Arrays.asList(  "QF-Test/Incidents/"
                                                                                                          .concat("Warning").concat("/")
                                                                                                          .concat(this.rc.getCurrentTestCase().getName()).concat("/")
                                                                                                          .concat(event.getNode().getTreeName()).split("/") ),
                                                                                          event.getTimestamp()  )  ).unit("warnings")
                                                                                                                    .value(Double.valueOf(Integer.valueOf(event.getWarnings()).doubleValue()))
                                                                                                                    .url("")
                                                                                                                    .status(xStatus)
                                                                                                                    .build());
                                             */
                                            break;
        default                           : break;
      }

    } catch (GeneralSecurityException | IOException | URISyntaxException | NeotysAPIException e)
    {
      if(null != this.rc)
        this.rc.logError("AbstractNeoloadDataExchangeListener.problemOccurred(TestRunEvent event) =[0]=> ".concat(e.toString()));
    }
    catch(Exception f)
    {
      if(null != this.rc)
        this.rc.logError("AbstractNeoloadDataExchangeListener.problemOccurred(TestRunEvent event) =[1]=> ".concat(f.toString()));
    }

  }

  /**
   *  \brief    Diese Methode meldet, wenn erforderlich, die Dauer für die Ausführung des Knotens
   *
   *  Diese Methode wird von QF-Test immer dann aufgerufen, wenn ein Knoten
   *  verlassen wird.
   *
   *  Aus Sicht des Listeners ist dieser Zeitpunkt der richtige, um die Dauer
   *  für den Knoten über das Data Exchande API zu übermitteln. Da der Listener
   *  nicht von allen Knoten die Dauer der Ausführung überträgt, wird zunächst
   *  geprüft, ob es sich um einen Knoten handelt, dessen Dauer zu übertragen
   *  ist.
   *
   *  \param    event         In dem Parameter übergibt QF-Test die
   *                          Informationen über den Knoten, der verlassen
   *                          wird und Hinweise zum aktuellen Status der
   *                          Testausführung.
   */
  @Override
  public void nodeExited(TestRunEvent event)
  {
    try
    {
      if(     ( event.getNode().getType().matches( this.getObservedNodeRegEx() )  )
          &&  ( this.mapQualifiedNames.containsKey(event.getNode().getId())       ) )
      { // nur wenn der Knoten bekannt ist und der Typ übereinstimmt, sollen Daten übermittelt werden.
        Status xStatus = (new com.neotys.rest.dataexchange.model.StatusBuilder()).code("0").message("").state(State.PASS).build();
        try
        {
          this.getClient().addEntries(this.mapQualifiedNames.get(event.getNode().getId()).getEntries(event, xStatus, getRealtimeInS(), getDurationInS()));
        } catch (GeneralSecurityException | IOException | URISyntaxException | NeotysAPIException e)
        {
          this.rc.logError("AbstractNeoloadDataExchangeListener.nodeExited(TestRunEvent event) =[0]=> ".concat(e.toString()));
        }
        this.mapQualifiedNames.get(event.getNode().getId()).resetAllSignals();
      }
    }
    catch(NullPointerException n)
    {
      //  Der Block fängt NullPointerExceptions ab, die in der Vorbereitung vorkommen können.
    }
    catch (Exception e)
    {
      if(null != this.rc)
        this.rc.logError("AbstractNeoloadDataExchangeListener.nodeExited(TestRunEvent event) =[1]=> ".concat(e.toString()));
    }
 }


  /**
   *  \brief    In dieser Methode ist zu implementieren, wie die Dauer des aktuellen Knotens ermittelt wird.
   *
   *  Die Dauer eines Knoten ist soll die Zeit sein, die inklusive Pausen
   *  in einem Knoten verbraucht wurde. Mit dieser Methode soll diese Metrik
   *  ermittelt werden können.
   *
   *  \note     Diese Methode kann in diesem Kontext nicht implementiert werden,
   *            da in den zur Verfügung gestellten Schnittstellen die Methode
   *            StepLog.getDuration() nicht zur Verfügung steht.
   *
   *  @return   Die Methode soll die Dauer als rationale Zahl in
   *            Sekunden zurückgeben.
   */
  protected abstract double getDurationInS();

  /**
   *  \brief    In dieser Methode ist zu implementieren, wie die Verarbeitungszeit des aktuellen Knotens ermittelt wird.
   *
   *  Die Verarbeitungszeit eines Knoten ist soll die Zeit sein, die für das
   *  Ausführung von Benutzeraktionen benötigt wurde.
   *
   *  \note     Diese Methode kann in diesem Kontext nicht implementiert werden,
   *            da in den zur Verfügung gestellten Schnittstellen die Methode
   *            StepLog.getRealtime() nicht zur Verfügung steht.
   *
   *  @return   Die Methode soll die Verarbeitungszeit als rationale Zahl in
   *            Sekunden zurückgeben.
   */
  protected abstract double getRealtimeInS();
}

/*!
 *  \page       pNeoLoadDataExchangeAPI Messung der End User Experience im Lasttest mit QF-Test
 *
 *  \tableofcontents
 *
 *  \section    pNLEUE_Einleitung       Einleitung
 *
 *  \subsection pNLEUE_Motivation       Motivation
 *
 *
 *
 *  \subsection pNLEUE_Kurzbeschreibung Kurze Funktionsbeschreibung
 *
 *  \image html ablauf_messung_eue.png
 *
 *  \section    pNLEUE_Grenzen          Grenzen der Lösung
 *
 *  \subsection pNLEUE_GrFunktion       Messwerte stehen in NeoLoad Web nicht zur Verfügung
 *
 *  Der Listener berichtet über das DataExchange API die Messwerte von QF-Test
 *  an NeoLoad. Dort werden diese unter der Rubrik External Data zur Anzeige
 *  gebracht.
 *
 *  In der aktuellen Version von NeoLoad Web werden die Daten aktuell nicht
 *  dargestellt. Dieser Umstand ist aktuell eine gravierende Einschränkung, da
 *  NeoLoad Web die Zusammenarbeit von Teams fördern soll. Durch die fehlende
 *  Funktionalität ist die Zusammenarbeit stark eingeschränkt, da die Ergebnisse
 *  nur im Projekt mit einer Installation von NeoLoad eingesehen werden können.
 *
 *  Hinzu kommt, dass die Ergebnisse sich auch nicht beobachten lassen, wenn
 *  der Test im Batch-Modus läuft. In diesem Fall ist NeoLoad Web die empfohlene
 *  Sicht auf laufende Testergebnisse.
 *
 *  \xrefitem   opneo "Offener Punkt (Neotys)" "Offene Punkte für Neotys"
 *              Um NeoLoad Web nutzen zu können, müssen die Messwerte dort auch
 *              bereitgestellt werden. Dazu gibt es grundsätzlich die folgenden
 *              Handlungsmöglichkeiten:<br/>
 *              &nbsp;&nbsp;&nbsp;&bull; Integration von External Data in NeoLoad Web<br/>
 *              &nbsp;&nbsp;&nbsp;&bull; Darstellung der Messwerte unterhalb der Advanced Action<br/>
 *
 *
 *  \subsection pNLEUE_GrStatistik      Statistische Auswertungen sind schwer möglich
 *
 *  In der Auswertung bezüglich der Dauer stehen dem Anwender für Requests drei Metriken zur Verfügung:
 *  \li minimale Dauer
 *  \li durchschnittliche Dauer
 *  \li maximale Dauer
 *
 *  Eine solche Angabe lässt sich für die Messwerte aus QF-Test nur schwer
 *  erreichen, da NeoLoad über zu den erhaltenen Messwerten keine eigenen
 *  Auswertungen macht, was auch verständlich ist.
 *
 *  Bezogen auf einen Lastgenerator mit QF-Test lassen sich diese Messwerte
 *  nat�rlich durch den Listener ermitteln und übertragen. Wenn der Listener
 *  diese Aufgabe übernimmt, dann wirkt sich das auf die Ausführungsdauer des
 *  QF-Test Skript aus und verfälscht damit zumindest etwas die Messwerte.
 *
 *  Sobald aber mehr als ein Lastgenerator mit QF-Test eingesetzt wird, ist
 *  es fast nicht mehr möglich, diese Berechnung mit den aktuellen Mitteln
 *  zu erreichen, da die beiden Lastgeneratoren nicht miteinander
 *  kommunizieren. Somit wäre im Grunde ein Proxy zu entwickeln, über den
 *  jeder Listener seine Messwerte an den Controller übergibt. Dieser
 *  Proxy kennt dann alle Messwerte und kann daraus statistische Werte
 *  bilden und diese an den NeoLoad Controller übergeben.
 *
 *  Dieser Aufwand kann einfacher vermieden werden, wenn die Messwerte von
 *  NeoLoad selbst entsprechend verarbeitet und aufbereitet werden.
 *
 *  \xrefitem   opneo "Offener Punkt (Neotys)" "Offene Punkte für Neotys"
 *              Die Messwerte, die von dem Listener gesendet werden, sollen
 *              von NeoLoad nicht als external Data behandelt werden.<br/>
 *              Wenn NeoLoad den fachlichen Hintergrund der übertragenen Daten
 *              kennt, dann kann NeoLoad die Messwerte entsprechend darstellen
 *              und die gewohnten statistischen Messwerte ebenfalls ergänzen
 *              und dem Anwender zur Auswertung bereitstellen.
 *
 *
 *  \subsection pNLEUE_GrUsability      Wiedererkennung der Struktur und Messwerte ist erschwert
 *
 *  Wenn Daten über das DataExchange API übergeben werden, dann ist der Anwender
 *  sehr frei in der Gestaltung der Hierarchie. Damit lassen sich grundsätzlich
 *  Strukturen erstellen, die eine Auswertung erleichtern können.
 *
 *  Die Tatsache, dass grundsätzlich für den Messwert und die übergeordneten
 *  Knoten keine eigenen Grafiken verwendet werden können. limitiert die
 *  Möglichkeit einer möglichst ähnlichen Darstellung der Sachverhalte.
 *
 *  Die Verwendung der Symbolik auf QF-Test für die unterschiedlichen Knoten
 *  kann hier die Auffindbarkeit deutlich erhöhen.
 *
 *  Die Anforderung, die die Messwerte unter der Advanced Action bereitzustellen wurde im
 *  Abschnitt \ref pNLEUE_GrStatistik
 *  bereits dokumentiert.
 *
 *
 *  \subsection pNLEUE_GrAdministration Hoher administrativer Aufwand
 *
 *  Die aktuelle Lösung basiert auf den Möglichkeiten, die NeoLoad und QF-Test
 *  bereitstellen. Da sich bestimmte Schritte auch bedingen, muss der Anwender
 *  selbst dafür Sorge tragen, dass die Schritte in der richtigen Reihenfolge
 *  zur Ausführung gebracht werden.
 *
 *  Hier ist vorstellbar, dass die Toolhersteller mit einer Integration dem
 *  Anwender die Aufgabe etwas erleichtern.
 *
 *  \xrefitem   opqfs "Offener Punkt (QFS)" "Offene Punkte für QFS"
 *              In QF-Test ist der entwickelte Listener durch einen
 *              entsprechenden Prozeduraufruf zu registrieren.<br/>
 *              Durch einen geeigneten Schalter in der Kommandozeile könnte
 *              der Listener automatisch registriert werden.
 *
 *  \xrefitem   opneo "Offener Punkt (Neotys)" "Offene Punkte für Neotys"
 *              In der Vorbereitung eines Lasttest kann der Lastgenerator
 *              erkennen, dass in dem Skript eine Advanced Action QF-Test
 *              enthalten ist und automatisch den QF-Test Daemon starten.
 *              Mit diesem Start kann dann auch der Listener registriert
 *              werden.
 *
 *
 *  \subsection pNLEUE_GrTestumgebung   übergabe der Projektdaten an Lastgenerator ist schwierig
 *
 *  In den aktuellen Projekten stellt sich immer wieder die Frage, wie die
 *  notwendigen Daten an die Lastgeneratoren verteilt werden sollen.
 *
 *  Aus Gründen der einfachen Referenzierbarkeit bietet es sich an, das
 *  Verzeichnis \${NL-CustomResources} zu nutzen. über dieses Verzeichnis
 *  könnten QF-Test-Testsuiten, Libraries und die Lizenzdatei übergeben
 *  werden.
 *
 *  Dieses Vorgehen vereinfacht die Arbeit für den Anwender, da auf dem
 *  Lastgenerator dann lediglich die Grundinstallation von QF-Test vorhanden
 *  sein muss.
 *
 *  Aktuell ist ein solches Vorgehen so einfach nicht möglich, da NeoLoad nur
 *  die Dateien im Ordner \${NL-CustomResources} überträgt und Unterordner mit
 *  Inhalt ignoriert. Ohne Anlage von Unterordnern ergibt sich ein
 *  "Durcheinander" an Dateien, das nur schwer zu überblicken ist.
 *
 *  \xrefitem   opneo "Offener Punkt (Neotys)" "Offene Punkte für Neotys"
 *              Das Verzeichnis \${NL-CustomResources} ist in einem
 *              NeoLoad-Projekt ein guter Ablageort für Inhalte, die für den
 *              Lasttest benötigt werden und an die Lastgeneratoren übergeben
 *              werden sollen.<br/>
 *              Aktuell werden aber Unterordner und deren Inhalte nicht an
 *              die Lastgeneratoren übertragen. Das erschwert eine strukturierte
 *              Ablage der Inhalte, da alle Inhalte direkt in dem Verzeichnis
 *              abzulegen sind.<br/>
 *              Es ist zumindest zu überlegen, ob nicht ein Unterordner QF-Test
 *              übertragen werden kann, wenn klar ist, dass der Lastgenerator
 *              eine Advanced Action QF-Test ausführen muss.
 *
 */
//  \xrefitem   opqfs "Offener Punkt (QFS)" "Offene Punkte für QFS"
//  \xrefitem   opneo "Offener Punkt (Neotys)" "Offene Punkte für Neotys"