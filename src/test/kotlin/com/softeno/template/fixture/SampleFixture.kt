@file:JvmName("SampleFixture")
package com.softeno.template.fixture

import com.softeno.template.sample.http.dto.SampleResponseDto

class SampleResponseDtoFixture {
    companion object {
        @JvmStatic
        fun someDto(data: String) = SampleResponseDto(data)
    }
}