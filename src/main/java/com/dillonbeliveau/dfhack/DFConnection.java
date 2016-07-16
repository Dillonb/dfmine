package com.dillonbeliveau.dfhack;

import lombok.Getter;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;

@Component
public class DFConnection {
    @Value("${dfhack.host}")
    String host;
    @Value("${dfhack.port}")
    Integer port;

    private final byte[] REQUEST_MAGIC = "DFHack?\n".getBytes(Charset.forName("ASCII"));
    private final byte[] RESPONSE_MAGIC = "DFHack!\n".getBytes(Charset.forName("ASCII"));
    private final Integer PROTOCOL_VERSION = 1;

    Logger log = LogManager.getLogger(DFConnection.class);

    Socket connection;
    OutputStream connection_output;
    InputStream connection_input;

    @Getter
    boolean connected = false;

    @Scheduled(fixedDelay = 5000)
    private void attemptConnection() {
        if (!connected) {
            log.info("Attempting to connect to DF...");
            try {
                connection = new Socket(host, port);
                connection_output = connection.getOutputStream();
                connection_input = connection.getInputStream();

                byte[] request = ByteBuffer
                        .allocate(4 + REQUEST_MAGIC.length)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .put(REQUEST_MAGIC)
                        .putInt(PROTOCOL_VERSION)
                        .array();

                connection_output.write(request, 0, request.length);
                connection_output.flush();

                byte[] buffer = new byte[RESPONSE_MAGIC.length];

                connection_input.read(buffer, 0, buffer.length);

                if (Arrays.equals(buffer, RESPONSE_MAGIC)) {
                    connected = true;
                    log.info("Connection successful!");
                }
                else {
                    log.info("Received incorrect response magic.");
                    connected = false;
                }

            } catch (IOException e) {
                connected = false;
                log.info("Connection failed.");
            }
        }
    }

    @PostConstruct
    private void init() {
    }
}