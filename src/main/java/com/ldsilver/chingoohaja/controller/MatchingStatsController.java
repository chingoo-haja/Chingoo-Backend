package com.ldsilver.chingoohaja.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/matching/stats")
@RequiredArgsConstructor
@Tag(name = "매칭 통계", description = "실시간 매칭 대기열 및 통계 조회 API")
@SecurityRequirement(name = "Bearer Authentication")
public class MatchingStatsController {


}
