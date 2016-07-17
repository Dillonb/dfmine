package com.dillonbeliveau.dfhack.remotefunction;

import com.dfhack.protobuf.dfproto.CoreProtocol;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.Parser;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * Created by dillon on 7/16/16.
 */
public class RemoteFunction<Input extends GeneratedMessageLite, Output extends GeneratedMessageLite> {
    private final int MAX_MESSAGE_SIZE = 64 * 1048576;
    private CoreProtocol.CoreBindReply bindReply = null;
    private Socket socket;
    Parser<Output> outputParser;

    private static final Logger log = LogManager.getLogger(RemoteFunction.class);

    private final short RPC_REPLY_RESULT = -1;
    private final short RPC_REPLY_FAIL = -2;
    private final short RPC_REPLY_TEXT = -3;
    private final short RPC_REQUEST_QUIT = -4;


    String byteBufferToString(byte[] buffer) {
        char[] temp = new char[buffer.length];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        CharBuffer charBuffer = Charset.forName("ASCII").decode(byteBuffer);
        return charBuffer.toString();
    }

    ByteString stringToByteString(String string) {
        return ByteString.copyFrom(string.getBytes(Charset.forName("ASCII")));
    }

    public RemoteFunction(String inputPlugin, Class<Input> inputClass,
                          String outputPlugin, Class<Output> outputClass, Parser<Output> outputParser,
                          String method, Socket socket) throws IOException {
        this(inputPlugin, inputClass,
                outputPlugin, outputClass, outputParser,
                "", method, socket);
    }
    public RemoteFunction(String inputPlugin, Class<Input> inputClass,
                          String outputPlugin, Class<Output> outputClass, Parser<Output> outputParser,
                          String plugin, String method, Socket socket) throws IOException {
        this.socket = socket;
        this.outputParser = outputParser;

        log.debug("Binding remote method " + plugin + "::" + method + "...");

        CoreProtocol.CoreBindRequest.Builder requestBuilder = CoreProtocol.CoreBindRequest.newBuilder()
                .setMethod(method)
                .setInputMsg(inputPlugin + "." + inputClass.getSimpleName())
                .setOutputMsg(outputPlugin + "." + outputClass.getSimpleName());

        if (!plugin.equals("")) {
            requestBuilder.setPluginBytes(stringToByteString(plugin));
        }

        byte[] command = requestBuilder.build().toByteArray();

        RPCMessageHeader header = new RPCMessageHeader();
        header.setId((short)0);
        header.setSize(command.length);

        byte[] header_buffer = header.getBytes();

        socket.getOutputStream().write(
                ByteBuffer.allocate(command.length + header_buffer.length).order(ByteOrder.LITTLE_ENDIAN)
                        .put(header_buffer)
                        .put(command)
                        .array());

        bindReply = CoreProtocol.CoreBindReply.parseFrom(getResponse(socket));
    }

    private byte[] getResponse(Socket socket) throws IOException {
        RPCMessageHeader responseHeader;
        DataInputStream inputStream = new DataInputStream(socket.getInputStream());

        do {
            byte[] replyHeaderBuffer = new byte[8];
            inputStream.readFully(replyHeaderBuffer);
            responseHeader = new RPCMessageHeader(replyHeaderBuffer);
            switch (responseHeader.getId()) {
                case RPC_REPLY_TEXT:
                    byte[] textBuffer = new byte[responseHeader.getSize()];
                    inputStream.readFully(textBuffer);
                    String text = byteBufferToString(textBuffer);
                    log.info("Received text reply: " + text);
                    break;
                case RPC_REPLY_FAIL:
                    log.error("Failed with error code: " + responseHeader.getSize());
                    break;
                case RPC_REPLY_RESULT:
                    log.debug("Received header indicating a success. Response size: " + responseHeader.getSize());
                    log.debug("Remaining on stream: " + inputStream.available());
            }
        } while (responseHeader.getId() != RPC_REPLY_RESULT);

        byte[] reply = new byte[responseHeader.getSize()];
        log.debug("Allocated space for a response size of: " + reply.length);
        DataInputStream ds = new DataInputStream(inputStream);
        ds.readFully(reply);

        log.debug("Read " + reply.length + " bytes. " + inputStream.available() + " bytes remain on stream.");
        return reply;
    }

    public Output execute(Input arg) throws IOException {
        byte[] input_buffer = arg.toByteArray();
        byte[] header_buffer = new RPCMessageHeader((short) bindReply.getAssignedId(), input_buffer.length).getBytes();

        socket.getOutputStream().write(
                ByteBuffer.allocate(input_buffer.length + header_buffer.length).order(ByteOrder.LITTLE_ENDIAN)
                        .put(header_buffer)
                        .put(input_buffer)
                        .array());
        socket.getOutputStream().flush();

        byte[] replyBuffer = getResponse(socket);

        Output output = outputParser.parseFrom(replyBuffer);
        return output;
    }
}
