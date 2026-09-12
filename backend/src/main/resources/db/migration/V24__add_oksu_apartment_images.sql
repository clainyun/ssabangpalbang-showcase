-- BE-004: add the separately curated Oksu-dong apartment illustrations.
WITH image_source (
    complex_code,
    object_key,
    sha256,
    source_slug,
    source_csv,
    match_method,
    match_confidence
) AS (
    VALUES
        ('A10026748', 'apartment-images/v1/A10026748.webp', '185e775e5fcdbc10015d5b874363a352c90fe8f7b912a2a0458011d1ff6ac0cf', 'e-pyeonhansesang-oksu-parkhills', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'verified-oksu-alias', 'HIGH'),
        ('A13310004', 'apartment-images/v1/A13310004.webp', 'e439fa50cf34564937e44e77eed778b20e732460526e6791aac0f2f181a6546a', 'oksu-kukdong', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'name+district+dong+households', 'HIGH'),
        ('A13375901', 'apartment-images/v1/A13375901.webp', 'b5e4aeab632a4304e99fc0a2cfebf9c4e114c58ec275f56003585ebfbbec4d5c', 'hannam-heights', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'name+district+dong+households', 'HIGH'),
        ('A13375902', 'apartment-images/v1/A13375902.webp', 'c7c84a7bf44dec5c9594c7ee41c45fe862b156bbbb017a560a72946be1000718', 'oksu-samsung', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'name+district+dong+households', 'HIGH'),
        ('A13375903', 'apartment-images/v1/A13375903.webp', '5c05d423e070cd421124122add945bbd41ae820e5e57985d339ce526701cabdc', 'oksu-riverside-poonglim-iwon', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'name+district+dong+households', 'HIGH'),
        ('A13375905', 'apartment-images/v1/A13375905.webp', 'c7c84a7bf44dec5c9594c7ee41c45fe862b156bbbb017a560a72946be1000718', 'oksu-samsung', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'verified-oksu-shared-complex', 'HIGH'),
        ('A13375906', 'apartment-images/v1/A13375906.webp', '8bfa7f4a771d718da6e66388c2eeee35b5bedba0018012c590cd9640c687bc97', 'oksu-eoullim', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'verified-oksu-alias', 'HIGH'),
        ('A13375907', 'apartment-images/v1/A13375907.webp', 'fd72b2b1d34720c5eb7d6e084a5d0a892e6a7870bfc766e9c0666829fd76d2c3', 'raemian-oksu-riverzen', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'name+district+dong+households', 'HIGH'),
        ('A13375908', 'apartment-images/v1/A13375908.webp', 'fd72b2b1d34720c5eb7d6e084a5d0a892e6a7870bfc766e9c0666829fd76d2c3', 'raemian-oksu-riverzen', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'verified-oksu-shared-complex', 'HIGH'),
        ('A13376702', 'apartment-images/v1/A13376702.webp', '9f1ae760e1eff99c339eddff46c3c8ea61137e84fb91cbaa9074cacff5a47b31', 'oksu-hyundai', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'name+district+dong+households', 'HIGH'),
        ('A13383801', 'apartment-images/v1/A13383801.webp', 'f19ae0cfb2d3bb38c63970df9e8902a9cc656952797b26491966166b7c8fa202', 'oksu-heights', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'verified-oksu-alias', 'HIGH'),
        ('A13384403', 'apartment-images/v1/A13384403.webp', '072a001e8d144db08df85b2ba899da53a75575c1dad2f430851b22acb704d289', 'oksu-kukdong-green', 'outputs/ui-redesign/assets/oksu-apartments/manifest.json', 'verified-oksu-alias', 'HIGH')
)
INSERT INTO apartment_image (
    apartment_id,
    object_key,
    sha256,
    source_slug,
    source_csv,
    match_method,
    match_confidence
)
SELECT
    apartment.id,
    image_source.object_key,
    image_source.sha256,
    image_source.source_slug,
    image_source.source_csv,
    image_source.match_method,
    image_source.match_confidence
FROM image_source
JOIN apartment ON apartment.complex_code = image_source.complex_code;
