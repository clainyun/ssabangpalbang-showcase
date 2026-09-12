package com.ssafy.ssabangpalbang.apartment.dto.response;

public record ApartmentFavoriteResponse(
        Long apartmentId,
        boolean favoritedByMe,
        Integer favoriteCount
) {
    public static ApartmentFavoriteResponse of(
            Long apartmentId,
            boolean favoritedByMe,
            long favoriteCount
    ) {
        return new ApartmentFavoriteResponse(
                apartmentId,
                favoritedByMe,
                (int) favoriteCount
        );
    }
}
