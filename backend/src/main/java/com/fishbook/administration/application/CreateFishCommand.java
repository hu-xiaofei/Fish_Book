package com.fishbook.administration.application;

public record CreateFishCommand(String slug, FishContentCommand content) {}
