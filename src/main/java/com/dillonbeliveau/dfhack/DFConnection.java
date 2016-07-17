package com.dillonbeliveau.dfhack;

import com.dfhack.protobuf.RemoteFortressReader.RemoteFortressReader;
import com.dfhack.protobuf.dfproto.CoreProtocol;
import com.dillonbeliveau.dfhack.remotefunction.RemoteFunction;
import lombok.Getter;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private static final Logger log = LogManager.getLogger(DFConnection.class);

    Socket connection;
    OutputStream connection_output;
    InputStream connection_input;

    @Getter
    boolean connected = false;

    // RemoteFunction<CoreProtocol.CoreBindRequest, CoreProtocol.CoreBindReply> bindMethodCall;
    RemoteFunction<CoreProtocol.EmptyMessage, CoreProtocol.IntMessage> coreSuspendCall;
    RemoteFunction<CoreProtocol.EmptyMessage, CoreProtocol.IntMessage> coreResumeCall;

    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.ViewInfo> rfrViewInfoCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.ScreenCapture> rfrCopyScreenCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.UnitList> rfrUnitListCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.WorldMap> rfrWorldMapCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.RegionMaps> rfrRegionMapCall;

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
                byte[] versionBuffer = new byte[4];
                connection_input.read(buffer, 0, buffer.length);
                connection_input.read(versionBuffer, 0, versionBuffer.length);
                int responseProtocolVersion = ByteBuffer.wrap(versionBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

                if (Arrays.equals(buffer, RESPONSE_MAGIC) &&  responseProtocolVersion == PROTOCOL_VERSION) {
                    connected = true;
                    log.info("Connection successful! Binding RPCs...");

                    coreSuspendCall = new RemoteFunction<>(
                            "dfproto", CoreProtocol.EmptyMessage.class,
                            "dfproto", CoreProtocol.IntMessage.class, CoreProtocol.IntMessage.PARSER,
                            "CoreSuspend", connection);

                    coreResumeCall = new RemoteFunction<>(
                            "dfproto", CoreProtocol.EmptyMessage.class,
                            "dfproto", CoreProtocol.IntMessage.class, CoreProtocol.IntMessage.PARSER,
                            "CoreResume", connection);

                    rfrViewInfoCall = new RemoteFunction<>(
                            "dfproto", CoreProtocol.EmptyMessage.class,
                            "RemoteFortressReader", RemoteFortressReader.ViewInfo.class, RemoteFortressReader.ViewInfo.PARSER,
                            "RemoteFortressReader", "GetViewInfo", connection);

                    rfrCopyScreenCall = new RemoteFunction<>(
                            "dfproto", CoreProtocol.EmptyMessage.class,
                            "RemoteFortressReader", RemoteFortressReader.ScreenCapture.class, RemoteFortressReader.ScreenCapture.PARSER,
                            "RemoteFortressReader", "CopyScreen", connection);
                    rfrUnitListCall = new RemoteFunction<>(
                            "dfproto", CoreProtocol.EmptyMessage.class,
                            "RemoteFortressReader", RemoteFortressReader.UnitList.class, RemoteFortressReader.UnitList.PARSER,
                            "RemoteFortressReader", "GetUnitList", connection);
                    rfrWorldMapCall = new RemoteFunction<>(
                            "dfproto", CoreProtocol.EmptyMessage.class,
                            "RemoteFortressReader", RemoteFortressReader.WorldMap.class, RemoteFortressReader.WorldMap.PARSER,
                            "RemoteFortressReader", "GetWorldMap", connection);
                    rfrRegionMapCall = new RemoteFunction<>(
                            "dfproto", CoreProtocol.EmptyMessage.class,
                            "RemoteFortressReader", RemoteFortressReader.RegionMaps.class, RemoteFortressReader.RegionMaps.PARSER,
                            "RemoteFortressReader", "GetRegionMaps", connection);

                    log.info("Successfully bound all RPCs!");
                    init();

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

    private RemoteFortressReader.ViewInfo viewInfo;
    private RemoteFortressReader.ScreenCapture screenCapture;
    private RemoteFortressReader.UnitList unitList;
    private RemoteFortressReader.WorldMap worldMap;
    private RemoteFortressReader.RegionMaps regionMaps;


    private void init() throws IOException {
        // Get some initial stuff
        // Necessary for initialization, apparently.
        coreSuspendCall.execute(CoreProtocol.EmptyMessage.getDefaultInstance());
        viewInfo = rfrViewInfoCall.execute(CoreProtocol.EmptyMessage.getDefaultInstance());
        screenCapture = rfrCopyScreenCall.execute(CoreProtocol.EmptyMessage.getDefaultInstance());
        unitList = rfrUnitListCall.execute(CoreProtocol.EmptyMessage.getDefaultInstance());
        worldMap = rfrWorldMapCall.execute(CoreProtocol.EmptyMessage.getDefaultInstance());
        regionMaps = rfrRegionMapCall.execute(CoreProtocol.EmptyMessage.getDefaultInstance());
        coreResumeCall.execute(CoreProtocol.EmptyMessage.getDefaultInstance());

        /*
        materialListCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.MaterialList>(networkClient, "GetMaterialList", "RemoteFortressReader");
        itemListCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.MaterialList>(networkClient, "GetItemList", "RemoteFortressReader");
        tiletypeListCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.TiletypeList>(networkClient, "GetTiletypeList", "RemoteFortressReader");
        blockListCall = CreateAndBind<RemoteFortressReader.BlockRequest, RemoteFortressReader.BlockList>(networkClient, "GetBlockList", "RemoteFortressReader");
        unitListCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.UnitList>(networkClient, "GetUnitList", "RemoteFortressReader");
        viewInfoCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.ViewInfo>(networkClient, "GetViewInfo", "RemoteFortressReader");
        mapInfoCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.MapInfo>(networkClient, "GetMapInfo", "RemoteFortressReader");
        mapResetCall = CreateAndBind<dfproto.EmptyMessage>(networkClient, "ResetMapHashes", "RemoteFortressReader");
        buildingListCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.BuildingList>(networkClient, "GetBuildingDefList", "RemoteFortressReader");
        worldMapCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.WorldMap>(networkClient, "GetWorldMap", "RemoteFortressReader");
        worldMapCenterCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.WorldMap>(networkClient, "GetWorldMapCenter", "RemoteFortressReader");
        regionMapCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.RegionMaps>(networkClient, "GetRegionMaps", "RemoteFortressReader");
        creatureRawListCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.CreatureRawList>(networkClient, "GetCreatureRaws", "RemoteFortressReader");
        plantRawListCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.PlantRawList>(networkClient, "GetPlantRaws", "RemoteFortressReader");
        keyboardEventCall = CreateAndBind<RemoteFortressReader.KeyboardEvent>(networkClient, "PassKeyboardEvent", "RemoteFortressReader");
        copyScreenCall = CreateAndBind<dfproto.EmptyMessage, RemoteFortressReader.ScreenCapture>(networkClient, "CopyScreen", "RemoteFortressReader");

         */

    }
}