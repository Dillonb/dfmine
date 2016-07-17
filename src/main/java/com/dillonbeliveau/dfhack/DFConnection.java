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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    DataOutputStream connection_output;
    DataInputStream connection_input;

    @Getter
    boolean connected = false;

    RemoteFunction<CoreProtocol.EmptyMessage, CoreProtocol.IntMessage> coreSuspendCall;
    RemoteFunction<CoreProtocol.EmptyMessage, CoreProtocol.IntMessage> coreResumeCall;

    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.ViewInfo> rfrViewInfoCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.ScreenCapture> rfrCopyScreenCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.UnitList> rfrUnitListCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.WorldMap> rfrWorldMapCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.RegionMaps> rfrRegionMapCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.MaterialList> rfrMaterialListCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.MaterialList> rfrItemListCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.TiletypeList> rfrTiletypeListCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.MapInfo> rfrMapInfoCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.BuildingList> rfrBuildingListCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.WorldMap> rfrWorldMapCenterCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.CreatureRawList> rfrCreatureRawListCall;
    RemoteFunction<CoreProtocol.EmptyMessage, RemoteFortressReader.PlantRawList> rfrPlantRawListCall;

    RemoteFunction<RemoteFortressReader.BlockRequest, RemoteFortressReader.BlockList> rfrBlockListCall;

    // TODO: Update RemoteFunction class to support calls with no response
    //RemoteFunction<CoreProtocol.EmptyMessage> rfrMapResetCall;

    // TODO: Update RemoteFunction class to support calls with no response
    //RemoteFunction<RemoteFortressReader.KeyboardEvent> rfrKeyboardEventCall;

    private RemoteFortressReader.ViewInfo viewInfo;
    private RemoteFortressReader.ScreenCapture screenCapture;
    private RemoteFortressReader.UnitList unitList;
    private RemoteFortressReader.WorldMap worldMap;
    private RemoteFortressReader.RegionMaps regionMaps;

    @Scheduled(fixedDelay = 5000)
    private void attemptConnection() {
         if (!connected) {
            log.info("Attempting to connect to DF...");
            try {
                connection = new Socket(host, port);
                connection_output = new DataOutputStream(connection.getOutputStream());
                connection_input = new DataInputStream(connection.getInputStream());

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
                connection_input.readFully(buffer, 0, buffer.length);
                connection_input.readFully(versionBuffer, 0, versionBuffer.length);
                int responseProtocolVersion = ByteBuffer.wrap(versionBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

                if (Arrays.equals(buffer, RESPONSE_MAGIC) &&  responseProtocolVersion == PROTOCOL_VERSION) {
                    log.info("Connection successful! Binding RPCs...");
                    bindRPCs();
                    log.info("Successfully bound all RPCs!");
                    init();
                    connected = true;
                }
                else {
                    log.error("Received incorrect response magic. Connection failed.");
                    connected = false;
                }
            } catch (IOException e) {
                connected = false;
                log.error("Connection failed.");
            }
        }
    }
    private CoreProtocol.EmptyMessage empty() {
        return CoreProtocol.EmptyMessage.getDefaultInstance();
    }

    private void bindRPCs() throws IOException {
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

        rfrMaterialListCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.MaterialList.class, RemoteFortressReader.MaterialList.PARSER,
                "RemoteFortressReader", "GetMaterialList", connection);

        rfrItemListCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.MaterialList.class, RemoteFortressReader.MaterialList.PARSER,
                "RemoteFortressReader", "GetItemList", connection);

        rfrTiletypeListCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.TiletypeList.class, RemoteFortressReader.TiletypeList.PARSER,
                "RemoteFortressReader", "GetTiletypeList", connection);

        rfrBlockListCall = new RemoteFunction<>(
                "RemoteFortressReader", RemoteFortressReader.BlockRequest.class,
                "RemoteFortressReader", RemoteFortressReader.BlockList.class, RemoteFortressReader.BlockList.PARSER,
                "RemoteFortressReader", "GetBlockList", connection);

        rfrMapInfoCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.MapInfo.class, RemoteFortressReader.MapInfo.PARSER,
                "RemoteFortressReader", "GetMapInfo", connection);

        rfrBuildingListCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.BuildingList.class, RemoteFortressReader.BuildingList.PARSER,
                "RemoteFortressReader", "GetBuildingDefList", connection);

        rfrWorldMapCenterCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.WorldMap.class, RemoteFortressReader.WorldMap.PARSER,
                "RemoteFortressReader", "GetWorldMapCenter", connection);

        rfrCreatureRawListCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.CreatureRawList.class, RemoteFortressReader.CreatureRawList.PARSER,
                "RemoteFortressReader", "GetCreatureRaws", connection);

        rfrPlantRawListCall = new RemoteFunction<>(
                "dfproto", CoreProtocol.EmptyMessage.class,
                "RemoteFortressReader", RemoteFortressReader.PlantRawList.class, RemoteFortressReader.PlantRawList.PARSER,
                "RemoteFortressReader", "GetPlantRaws", connection);
    }

    RemoteFortressReader.MaterialList materialList;
    RemoteFortressReader.MaterialList itemList;
    RemoteFortressReader.TiletypeList tiletypeList;
    RemoteFortressReader.BuildingList buildingList;
    RemoteFortressReader.CreatureRawList creatureRawList;
    RemoteFortressReader.PlantRawList plantRawList;

    private void init() throws IOException {
        // Grab some static information
        log.info("Loading static information:");
        log.info("Loading material list...");
        materialList = rfrMaterialListCall.execute(empty());
        log.info("Loaded " + materialList.getMaterialListCount() + " materials.");
        log.info("Loading item list...");
        itemList = rfrItemListCall.execute(empty());
        log.info("Loaded " + itemList.getMaterialListCount() + " items.");
        log.info("Loading tiletype list...");
        tiletypeList = rfrTiletypeListCall.execute(empty());
        log.info("Loaded " + tiletypeList.getTiletypeListCount() + " tiletypes.");
        log.info("Loading building list...");
        buildingList = rfrBuildingListCall.execute(empty());
        log.info("Loaded " + buildingList.getBuildingListCount() + " buildings.");
        log.info("Loading creature list...");
        creatureRawList = rfrCreatureRawListCall.execute(empty());
        log.info("Loaded " + creatureRawList.getCreatureRawsCount() + " creatures.");
        log.info("Loading plant list...");
        plantRawList = rfrPlantRawListCall.execute(empty());
        log.info("Loaded " + plantRawList.getPlantRawsCount() + " plants.");

        log.info("Loading dynamic information: (Suspending game execution)");
        // Must suspend the game before fetching this information
        coreSuspendCall.execute(empty());

        log.info("Loading view info...");
        viewInfo = rfrViewInfoCall.execute(empty());
        log.info("Loading screen capture...");
        screenCapture = rfrCopyScreenCall.execute(empty());
        log.info("Loading unit list...");
        unitList = rfrUnitListCall.execute(empty());
        log.info("Loading world map...");
        worldMap = rfrWorldMapCall.execute(empty());
        log.info("Loading region maps...");
        regionMaps = rfrRegionMapCall.execute(empty());

        log.info("All done! Resuming...");
        coreResumeCall.execute(empty());

    }
}