package com.hypersense.boot.framework.agents.profile;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(String profileId) {
        super("Capability profile not found or disabled: " + profileId);
    }
}
