package com.softeno.template.app.common

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

fun getPageRequest(page: Int, size: Int, sort: String, direction: String) =
    Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
        .let { PageRequest.of(page, size, it) }
