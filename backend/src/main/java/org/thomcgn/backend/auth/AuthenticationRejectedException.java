package org.thomcgn.backend.auth;

import org.springframework.http.HttpStatus;
import org.thomcgn.backend.common.exception.ApiException;

/** Authentication denial after a deliberate token revocation must not undo that revocation. */
public class AuthenticationRejectedException extends ApiException {
    public AuthenticationRejectedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
