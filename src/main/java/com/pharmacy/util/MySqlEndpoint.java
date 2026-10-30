package com.pharmacy.util;

import java.io.IOException;
import java.net.URI;

/** CLI backup/restore must use the same server and database as the JDBC connection. */
public record MySqlEndpoint(String host, String port, String database) {
    public static MySqlEndpoint fromJdbcUrl(String url) throws IOException {
        try {
            if(!url.startsWith("jdbc:mysql://"))throw new IllegalArgumentException();
            URI uri=URI.create(url.substring(5));
            String database=uri.getPath().substring(1);
            if(uri.getHost()==null || uri.getUserInfo()!=null || !database.matches("[A-Za-z0-9_$-]+"))throw new IllegalArgumentException();
            return new MySqlEndpoint(uri.getHost(),Integer.toString(uri.getPort()<0?3306:uri.getPort()),database);
        }catch(Exception ex){throw new IOException("Backup/restore requires a single-host MySQL JDBC URL with a database name.",ex);}
    }
}
