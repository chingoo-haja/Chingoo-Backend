package com.ldsilver.chingoohaja.service;

import com.ldsilver.chingoohaja.infrastructure.agora.AgoraCloudRecordingClient;
import com.ldsilver.chingoohaja.repository.CallRecordingRepository;
import com.ldsilver.chingoohaja.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgoraRecordingService {

    private final AgoraCloudRecordingClient cloudRecordingClient;
    private final CallRepository callRepository;
    private final CallRecordingRepository callRecordingRepository;
    private final FirebaseStorageService firebaseStorageService;
}
