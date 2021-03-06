/**********************************************************************
 * $Source: /cvsroot/hibiscus/hibiscus/src/de/willuhn/jameica/hbci/SynchronizeOptions.java,v $
 * $Revision: 1.10 $
 * $Date: 2010/09/02 12:25:13 $
 * $Author: willuhn $
 * $Locker:  $
 * $State: Exp $
 *
 * Copyright (c) by willuhn.webdesign
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.hbci;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;

import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;

/**
 * Container fuer die Synchronisierungsoptionen eines Kontos.
 */
public class SynchronizeOptions implements Serializable
{
  private String id = null;
  private boolean offline  = false;
  private boolean disabled = false;
  private final static Settings settings = Application.getPluginLoader().getPlugin(HBCI.class).getResources().getSettings();

  /**
   * Liefert die Liste der zu synchronisierenden Konten.
   * @return Liste der zu synchronisierenden Konten.
   */
  public static List<Konto> getSynchronizeKonten()
  {
    List<Konto> l = new LinkedList<Konto>();
    
    try
    {
      DBIterator konten = de.willuhn.jameica.hbci.Settings.getDBService().createList(Konto.class);
      // Konten-Sortierung immer gleich. Wir verwenden diese Sortierung, damit Konten,
      // die potentiell ueber das gleiche Sicherheitsmedium abgewickelt werden, direkt
      // aufeinander folgen. Das ermoeglicht eine spaetere Gruppierung in einem HBCI-Dialog
      // Anschliessend noch Bezeichnung wegen BUGZILLA 1346, Kommentar 12
      konten.setOrder("order by blz,bic,passport_class,bezeichnung");
      
      while (konten.hasNext())
      {
        Konto k = (Konto) konten.next();
        if (k.hasFlag(Konto.FLAG_DISABLED)) // deaktivierte Konten nicht beruecksichtigen
          continue;
        SynchronizeOptions o = new SynchronizeOptions(k);
        if (o.getSynchronize())
          l.add(k);
      }
    }
    catch (RemoteException re)
    {
      Logger.error("unable to load konto list",re);
    }

    return l;
  }

  /**
   * ct.
   * @param k das Konto.
   * @throws RemoteException
   */
  public SynchronizeOptions(Konto k) throws RemoteException
  {
    this.offline  = k.hasFlag(Konto.FLAG_OFFLINE);
    this.disabled = k.hasFlag(Konto.FLAG_DISABLED);
    this.id = k.getID();
  }
  
  /**
   * Prueft, ob irgendeine Synchronisierungsoption fuer das Konto aktiviert ist.
   * @return true, wenn irgendeine Option aktiv ist.
   */
  public boolean getSynchronize()
  {
    return getSyncSaldo() ||
           getSyncKontoauszuege() ||
           getSyncSepaDauerauftraege() ||
           getSyncAuslandsUeberweisungen() ||
           getSyncSepaLastschriften();
  }
  
  /**
   * Aendert den Synchronisierungsstatus aller Auftragstypen.
   * @param status neuer Status.
   */
  public void setAll(boolean status)
  {
    this.setSyncSaldo(status);
    this.setSyncKontoauszuege(status);
    this.setSyncSepaDauerauftraege(status);
    this.setSyncAuslandsUeberweisungen(status);
    this.setSyncSepaLastschriften(status);
  }

  /**
   * BUGZILLA 346
   * Prueft, ob die Kontoauszuege abgerufen werden sollen.
   * @return true, wenn sie synchronisiert werden sollen.
   */
  public boolean getSyncSaldo()
  {
    // Da das vorher mit ".kontoauszug" zusammengefasst
    // war, nehmen wir als Default-Wert auch den von dort.
    // Damit wird beim ersten mal der Vorwert uebernommen.
    // (Sanfte Migration)
    return !this.disabled && settings.getBoolean("sync.konto." + id + ".saldo",true);
  }

  /**
   * Prueft, ob die Kontoauszuege abgerufen werden sollen.
   * @return true, wenn sie synchronisiert werden sollen.
   */
  public boolean getSyncKontoauszuege()
  {
    return !this.disabled && settings.getBoolean("sync.konto." + id + ".kontoauszug",true);
  }
  
  /**
   * Prueft, ob die SEPA-Dauerauftraege synchronisiert werden sollen.
   * @return true, wenn sie synchronisiert werden sollen.
   */
  public boolean getSyncSepaDauerauftraege()
  {
    return !this.disabled && !this.offline && settings.getBoolean("sync.konto." + id + ".sepadauer",false);
  }

  /**
   * Prueft, ob offene und ueberfaellige Auslandsueberweisungen eingereicht werden sollen.
   * @return true, wenn sie synchronisiert werden sollen.
   */
  public boolean getSyncAuslandsUeberweisungen()
  {
    return !this.disabled && !this.offline && settings.getBoolean("sync.konto." + id + ".uebforeign",false);
  }

  /**
   * Prueft, ob offene und ueberfaellige SEPA-Lastschriften eingereicht werden sollen.
   * @return true, wenn sie synchronisiert werden sollen.
   */
  public boolean getSyncSepaLastschriften()
  {
    return !this.disabled && !this.offline && settings.getBoolean("sync.konto." + id + ".sepalast",false);
  }

  /**
   * Prueft, ob in dem Konto automatisch passende Gegenbuchungen angelegt werden sollen,
   * wenn es ein Offline-Konto ist.
   * @return true, wenn automatische Offline-Synchronisierung stattfinden soll.
   */
  public boolean getSyncOffline()
  {
    return !this.disabled && settings.getBoolean("sync.konto." + id + ".offline",true);
  }
  
  /**
   * Aktiviert/deaktiviert die automatische Saldenberechnung fuer Offlinekonten.
   * Wenn dieses Setting fals liefert, wird unter keinen Umstaenden der Saldo
   * automatisch berechnet.
   * @return true, wenn der Saldo des Offline-Kontos automatisch berechnet werden
   * soll, wenn kein Scripting-Backend vorhanden ist.
   */
  public boolean getAutoSaldo()
  {
    return !this.disabled && this.offline && settings.getBoolean("sync.konto." + id + ".autosaldo",true);
  }
  
  /**
   * Aktiviert/deaktiviert die automatische Saldenberechnung fuer Offlinekonten.
   * Wenn dieses Setting fals liefert, wird unter keinen Umstaenden der Saldo
   * automatisch berechnet.
   * @param b true, wenn der Saldo des Offline-Kontos automatisch berechnet werden
   * soll, wenn kein Scripting-Backend vorhanden ist.
   */
  public void setAutoSaldo(boolean b)
  {
    settings.setAttribute("sync.konto." + id + ".autosaldo",b);
  }
  
  /**
   * Legt fest, ob die Kontoauszuege abgerufen werden sollen.
   * @param b true, wenn sie synchronisiert werden sollen.
   */
  public void setSyncKontoauszuege(boolean b)
  {
    settings.setAttribute("sync.konto." + id + ".kontoauszug",b);
  }
  
  /**
   * Legt fest, ob die Salden abgerufen werden sollen.
   * @param b true, wenn sie synchronisiert werden sollen.
   */
  public void setSyncSaldo(boolean b)
  {
    settings.setAttribute("sync.konto." + id + ".saldo",b);
  }

  /**
   * Legt fest, ob die SEPA-Dauerauftraege synchronisiert werden sollen.
   * @param b true, wenn sie synchronisiert werden sollen.
   */
  public void setSyncSepaDauerauftraege(boolean b)
  {
    settings.setAttribute("sync.konto." + id + ".sepadauer",b);
  }
  
  /**
   * Legt fest, ob offene und ueberfaellige Auslandsueberweisungen eingereicht werden sollen.
   * @param b true, wenn sie synchronisiert werden sollen.
   */
  public void setSyncAuslandsUeberweisungen(boolean b)
  {
    settings.setAttribute("sync.konto." + id + ".uebforeign",b);
  }

  /**
   * Legt fest, ob offene und ueberfaellige SEPA-Lastschriften eingereicht werden sollen.
   * @param b true, wenn sie synchronisiert werden sollen.
   */
  public void setSyncSepaLastschriften(boolean b)
  {
    settings.setAttribute("sync.konto." + id + ".sepalast",b);
  }

  /**
   * Legt fest, ob in dem Konto automatisch passende Gegenbuchungen angelegt werden sollen,
   * wenn es ein Offline-Konto ist.
   * @param b true, wenn automatische Offline-Synchronisierung stattfinden soll.
   */
  public void setSyncOffline(boolean b)
  {
    settings.setAttribute("sync.konto." + id + ".offline",b);
  }
}
