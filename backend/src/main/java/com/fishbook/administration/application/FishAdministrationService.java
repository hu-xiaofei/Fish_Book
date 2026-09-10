package com.fishbook.administration.application;

public interface FishAdministrationService {
    AdminFishPageView search(AdminFishQuery query);
    AdminFishDetailView get(long id);
    AdminFishDetailView create(CreateFishCommand command);
    AdminFishDetailView update(long id, FishContentCommand command);
    AdminFishDetailView publish(long id);
    AdminFishDetailView unpublish(long id);
}
