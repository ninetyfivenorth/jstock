/*
 * JStock - Free Stock Market Software
 * Copyright (C) 2011 Yan Cheng CHEOK <yccheok@yahoo.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.yccheok.jstock.engine;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.yccheok.jstock.network.Utils.Type;

/**
 *
 * @author yccheok
 */
public class CurrencyExchangeMonitor {

    /**
     * Constructs an instance of CurrencyExchangeMonitor.
     * 
     * @param fromCountry convert from this country's currency
     * @param toCountry convert to this country's currency
     */
    public CurrencyExchangeMonitor(Country fromCountry, Country toCountry) {
        this.fromCountry = fromCountry;
        this.toCountry = toCountry;
    }

    /**
     * Starts currency exchange monitoring activities. This method can be called
     * multiple times. start/stop/suspend/resume are being synchronized with
     * each others.
     */
    public synchronized void start() {
        if (has_started) {
            return;
        }
        executor.submit(currencyExchangeRunnable);
        has_started = true;
    }

    /**
     * Stop all currency exchange monitoring activities. Once it is stopped, it
     * cannot be started again. This method will only return once it is being
     * terminated completely. start/stop/suspend/resume are being synchronized
     * with each others.
     */
    public synchronized void stop() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(100, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
            log.error(null, ex);
        }
    }

    /**
     * Temporary suspend all ongoing monitoring activities. This method can be
     * called multiple times. start/stop/suspend/resume are being synchronized
     * with each others.
     */
    public synchronized void suspend() {
        synchronized(currencyExchangeRunnable) {
            suspend = true;
        }
    }

    /**
     * Resume all ongoing monitoring activities. This method can be called
     * multiple times. start/stop/suspend/resume are being synchronized with
     * each others.
     */
    public synchronized void resume() {
        synchronized(currencyExchangeRunnable) {
            suspend = false;
            currencyExchangeRunnable.notify();
        }
    }

    // CurrencyExchangeRunnable is doing 2 things:
    //
    // (1) Keep trying to fill in countryToCurrencyCode with latest
    // country -> currency information, until it successes at least once.
    // (2) Fetch the latest currency exchange information, according to given
    // fromCountry and toCountry.
    private class CurrencyExchangeRunnable implements Runnable {

        @Override
        public void run() {
            while (!executor.isShutdown()) {
                try {
                    Thread.sleep(org.yccheok.jstock.gui.MainFrame.getInstance().getJStockOptions().getScanningSpeed());
                } catch (InterruptedException ex) {
                    log.error(null, ex);
                    // Usually triggered by executor.shutdownNow
                    return;
                }

                synchronized(this) {
                    while (suspend) {
                        try {
                            wait();
                        } catch (InterruptedException ex) {
                            log.error(null, ex);
                            // Usually triggered by executor.shutdownNow
                            return;
                        }
                    }
                }

                if (successUpdatedCountryToCurrencyCode == false) {
                    // Keep trying to fill in countryToCurrencyCode with latest
                    // country -> currency information, until it successes at
                    // least once.
                    final String server = org.yccheok.jstock.network.Utils.getURL(Type.CURRENCY_CODE_TXT);
                    Map<String, String> map = org.yccheok.jstock.gui.Utils.getUUIDValue(server);
                    for (Entry<String, String> entry : map.entrySet()) {
                        if (entry.getValue() != null) {
                            try {
                                countryToCurrencyCode.put(Country.valueOf(entry.getKey()), entry.getValue());
                            } catch (java.lang.IllegalArgumentException ex) {
                                // I am not sure whether I should set
                                // successUpdatedCountryToCurrencyCode to false.
                                log.error(null, ex);
                            }
                            // OK. We need not to contact CURRENCY_CODE_TXT's
                            // server anymore.
                            successUpdatedCountryToCurrencyCode = true;
                        }
                    }
                }
            }
        }
    }

    // This map, is used to convert a country to its latest assiciated currency.
    // This map will at most be updated by currencyExchangeRunnable once, from
    // CURRENCY_CODE_TXT's server. Even we are unable to obtain information
    // from CURRENCY_CODE_TXT's server, this map itself still able to provide
    // default information.
    private static final Map<Country, String> countryToCurrencyCode =  new ConcurrentHashMap<Country, String>();
    // Whether we had updated countryToCurrencyCode at least once?
    // Do I have to use volatile here? As this flag may write by a thread, but
    // read by another thread later.
    private static boolean successUpdatedCountryToCurrencyCode = false;

    static {
        countryToCurrencyCode.put(Country.Australia, "AUD");
        countryToCurrencyCode.put(Country.Austria, "EUR");
        countryToCurrencyCode.put(Country.Belgium, "EUR");
        countryToCurrencyCode.put(Country.Brazil, "BRL");
        countryToCurrencyCode.put(Country.Canada, "CAD");
        countryToCurrencyCode.put(Country.China, "CNY");
        countryToCurrencyCode.put(Country.Denmark, "DKK");
        countryToCurrencyCode.put(Country.France, "EUR");
        countryToCurrencyCode.put(Country.Germany, "EUR");
        countryToCurrencyCode.put(Country.HongKong, "HKD");
        countryToCurrencyCode.put(Country.India, "INR");
        countryToCurrencyCode.put(Country.Indonesia, "IDR");
        countryToCurrencyCode.put(Country.Italy, "EUR");
        countryToCurrencyCode.put(Country.Korea, "KPW");
        countryToCurrencyCode.put(Country.Malaysia, "MYR");
        countryToCurrencyCode.put(Country.Netherlands, "EUR");
        countryToCurrencyCode.put(Country.Norway, "NOK");
        countryToCurrencyCode.put(Country.Portugal, "EUR");
        countryToCurrencyCode.put(Country.Singapore, "SGD");
        countryToCurrencyCode.put(Country.Spain, "EUR");
        countryToCurrencyCode.put(Country.Sweden, "SEK");
        countryToCurrencyCode.put(Country.Switzerland, "CHF");
        countryToCurrencyCode.put(Country.Taiwan, "TWD");
        countryToCurrencyCode.put(Country.UnitedKingdom, "GBP");
        countryToCurrencyCode.put(Country.UnitedState, "USD");
    }

    /**
     * Convert from this country's currency.
     */
    private final Country fromCountry;

    /**
     * Convert to this country's currency.
     */
    private final Country toCountry;

    // Doesn't require volatile, as this variable is being accessed within
    // synchronized block.
    private boolean suspend = false;
    // Doesn't require volatile, as this variable is being accessed within
    // synchronized block.
    private boolean has_started = false;
    private final CurrencyExchangeRunnable currencyExchangeRunnable = new CurrencyExchangeRunnable();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Log log = LogFactory.getLog(CurrencyExchangeMonitor.class);
}