package org.example;

import com.github.markusbernhardt.proxy.ProxySearch;
import com.github.markusbernhardt.proxy.selector.misc.BufferedProxySelector;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.util.Hashtable;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
        proxySearch.setPacCacheSettings(32, 1000 * 60 * 5L, BufferedProxySelector.CacheScope.CACHE_SCOPE_HOST);
        ProxySelector selector = proxySearch.getProxySelector();
        ProxySelector.setDefault(selector);

        debugProxy(selector, "ldap://ldap.ca.notariato.org");

        String ldapUrl = "ldap://ldap.ca.notariato.org/cn%3dConsiglio%20Nazionale%20del%20Notariato%20Qualified%20Certification%20Authority%202019,ou%3dServizio%20Firma%20Digitale,o%3dConsiglio%20Nazionale%20del%20Notariato,c%3dIT?certificateRevocationList";

        byte[] crlData = ldapGet(ldapUrl);

        if (crlData != null) {
            saveCrlToFile(crlData, "downloaded_crl.crl");
        }
    }
    private static void debugProxy(ProxySelector selector, String url) {
        try {
            List<Proxy> proxies = selector.select(new URI(url));
            System.out.println("--- DEBUG PROXY ---");
            for (Proxy proxy : proxies) {
                System.out.println("Tipo Proxy: " + proxy.type());
                if (proxy.address() instanceof InetSocketAddress) {
                    InetSocketAddress addr = (InetSocketAddress) proxy.address();
                    System.out.println("Indirizzo: " + addr.getHostName() + ":" + addr.getPort());
                } else {
                    System.out.println("Indirizzo: DIRETTO (Nessun proxy)");
                }
            }
            System.out.println("-------------------\n");
        } catch (Exception e) {
            System.err.println("Errore durante il rilevamento proxy: " + e.getMessage());
        }
    }

    private static void saveCrlToFile(byte[] data, String fileName) {
        try (FileOutputStream fos = new FileOutputStream(new File(fileName))) {
            fos.write(data);
            System.out.println("CRL successfully saved to: " + fileName);
        } catch (Exception e) {
            System.err.println("Error saving CRL to file: " + e.getMessage());
        }
    }

    private static byte[] ldapGet(final String urlString) {
        final Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, urlString);

        // 4. LOG: Timeout e Debug LDAP
        env.put("com.sun.jndi.ldap.connect.timeout", "5000"); // 5 secondi
        env.put("com.sun.jndi.ldap.read.timeout", "5000");
        // Se fosse LDAP over SSL (ldaps), servirebbe anche questo:
        // env.put("java.naming.security.protocol", "ssl");

        try {
            // Use ";binary" to ensure the LDAP provider treats it as a byte array
            String attributeName = "certificateRevocationList;binary";
            final DirContext ctx = new InitialDirContext(env);
            final Attributes attributes = ctx.getAttributes("", new String[]{attributeName});

            if (attributes != null && attributes.get(attributeName) != null) {
                final Attribute attribute = attributes.get(attributeName);
                final byte[] ldapBytes = (byte[]) attribute.get();

                if (ldapBytes != null && ldapBytes.length > 0) {
                    System.out.println("Retrieved CRL size: " + ldapBytes.length + " bytes");
                    return ldapBytes;
                }
            }
        } catch (javax.naming.CommunicationException e) {
            System.err.println("!!! ERRORE DI COMUNICAZIONE: Possibile blocco firewall o proxy non configurato correttamente.");
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}