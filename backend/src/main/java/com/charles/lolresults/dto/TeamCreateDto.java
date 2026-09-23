package com.charles.lolresults.dto;

import jakarta.validation.constraints.NotBlank;

public record TeamCreateDto(@NotBlank String code, @NotBlank String name, String region) {}
