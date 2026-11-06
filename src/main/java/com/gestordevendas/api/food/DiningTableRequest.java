package com.gestordevendas.api.food;

import jakarta.validation.constraints.Size;

public record DiningTableRequest(@Size(max = 80) String name) { }
