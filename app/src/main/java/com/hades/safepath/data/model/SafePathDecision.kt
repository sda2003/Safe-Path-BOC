package com.hades.safepath.data.model

import com.hades.safepath.data.entity.GuidanceStep
import com.hades.safepath.data.entity.PackItem
import com.hades.safepath.data.entity.Shelter

data class SafePathDecision(
    val event: DisasterEvent,
    val route: List<RouteNode>,
    val shelter: Shelter,
    val guidanceSteps: List<GuidanceStep>,
    val packItems: List<PackItem>
)
