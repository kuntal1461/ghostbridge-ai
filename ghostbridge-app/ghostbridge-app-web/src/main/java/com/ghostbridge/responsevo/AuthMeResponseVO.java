package com.ghostbridge.responsevo;

import java.util.List;

/** The signed-in user as the UI needs it: name and role names (without the ROLE_ prefix). No credential, no hash. */
public record AuthMeResponseVO(String username, List<String> roles) {
}
