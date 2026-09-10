package dto.login;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Long expiresIn

) {

    public record UserSummaryLoginResponse(
            Long id,
            String fullName,
            String email

    ) {
    }

}
