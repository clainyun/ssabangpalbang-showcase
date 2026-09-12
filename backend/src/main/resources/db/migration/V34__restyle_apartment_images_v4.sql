-- BE-004: 시점·화풍을 강남·서초 기준으로 재생성한 아파트 이미지 61건을 v4로 교체한다.
--
-- V33에서 배포한 v3 이미지 중 61건은 배치는 정확했지만 카메라가 낮아 단지가
-- 옆에서 납작하게 보이거나, 건물 높이가 눌려 저층 단지처럼 표현됐다. 강남·서초
-- hybrid-v2 이미지를 스타일 기준으로 고각 조감으로 다시 생성해 교체한다.
-- 배치·동 수는 v3와 동일하게 유지했고 시점과 화풍만 바꿨다.
--
-- 검증 근거: 피사체 종횡비 평균 1.783 → 0.934, 납작(1.5 이상) 45건 → 2건
-- (남은 2건은 3~4동 소단지라 가로로 넓은 것이 자연스럽다).
--
-- v3 키를 그대로 덮지 않고 v4 키를 쓰는 이유는 nginx가 Cache-Control: immutable
-- (max-age=1년)로 서빙하기 때문이다. 같은 키로 파일만 바꾸면 기존 사용자에게
-- 전파되지 않는다. 기존 v2·v3 파일은 삭제하지 않으므로 롤백은 object_key를
-- 되돌리는 것만으로 즉시 가능하다.
--
-- 해시는 실제 업로드 대상 WebP 파일에서 직접 계산했다.

-- 검증용 대상 코드·기대값. 트랜잭션 종료 시 자동 삭제된다.
CREATE TEMP TABLE restyled_v4 (
    complex_code VARCHAR(20) PRIMARY KEY,
    object_key   VARCHAR(255) NOT NULL,
    sha256       CHAR(64) NOT NULL
) ON COMMIT DROP;

INSERT INTO restyled_v4 (complex_code, object_key, sha256) VALUES
        ('A10020006', 'apartment-images/v4/A10020006.webp', '368aaf296cf909e5a2e2e56db0574c6a1b51f8723df1258856fefd8a0efc884d'),
        ('A10020195', 'apartment-images/v4/A10020195.webp', '3e3a45338bba429d7cef3cc4e6b0fa32d2d296f56e604e1eeb8af7f58bbf25ef'),
        ('A10020230', 'apartment-images/v4/A10020230.webp', '2af03049e01e92d70042f8949c58d3fcf4bdafaca626606eb7bdb6d704601022'),
        ('A10020267', 'apartment-images/v4/A10020267.webp', '5b51ac7ef1b9c4621741b2cd47398403f9384ba3045fcdde6e54bf2956f441df'),
        ('A10020345', 'apartment-images/v4/A10020345.webp', '63c563fb6448856745a300797933b1802d2b05b3c22daa6a0aed72f21b8df1aa'),
        ('A10020555', 'apartment-images/v4/A10020555.webp', '3e32f54d0bc9dae0adfe2b59fbd497493b9e6f88b96c4db4376aec1b68475213'),
        ('A10020562', 'apartment-images/v4/A10020562.webp', '47d725bec5482779c27c8996c70c31a2b849dd02fb8d7adff6c91b5f43ec5808'),
        ('A10020716', 'apartment-images/v4/A10020716.webp', '073039233b9cbee66f8106e017dccefefb6cd04a48591b090386510afee0bb3b'),
        ('A10020731', 'apartment-images/v4/A10020731.webp', 'd70632d200cb9e39fd83c7c92fd80dceb52c119c8e2f89169ee8c06513bae5b9'),
        ('A10021859', 'apartment-images/v4/A10021859.webp', '4195781d4d079f550d48f30d0c0a7e3bbdf903938e1ca7b3e32b92497cfdb548'),
        ('A10022303', 'apartment-images/v4/A10022303.webp', 'e72241f39aff9864aeeb4090a9fab51c81bc84da64dd96b5139555f7ab556a77'),
        ('A10022362', 'apartment-images/v4/A10022362.webp', '6a6251f3493b3d6d04cb4eb1e39d72880d18fc593345bdddd8597e6659a1e5dc'),
        ('A10022480', 'apartment-images/v4/A10022480.webp', 'cf34cb1cd583c3c9391f334d8e461092a7ef4bb5f5995eae431abf3d538b91a0'),
        ('A10023296', 'apartment-images/v4/A10023296.webp', '9633f74282034b7016bb90b5831dfe6442e1623d041c4101559fcd66051d71a2'),
        ('A10023887', 'apartment-images/v4/A10023887.webp', '057c6bae2b8ab40c17314246c37d8bcbd9dd0b018f355ddd748d0b44c72d69a6'),
        ('A10024987', 'apartment-images/v4/A10024987.webp', '497c92491e9c5cdeded50362be761fc8b6aaad69212282b1813d38bf349ce00d'),
        ('A10026109', 'apartment-images/v4/A10026109.webp', '78f22a1b3df31d239a5e5afc788ec5f77172350d642172a91c3dfa8727bea794'),
        ('A10027136', 'apartment-images/v4/A10027136.webp', 'b65ccb70a3ed8fdf784caff1935d806713df6dc920f962694300bd258196b002'),
        ('A10027227', 'apartment-images/v4/A10027227.webp', 'cd8697820abc01e261af670c362e12d2b028c8d34b85cff907a41781baf39a79'),
        ('A10027504', 'apartment-images/v4/A10027504.webp', 'd788a2666a0c828e70029f3d032f88808784db53b3d4416e8e6149a1e69ba54e'),
        ('A10027632', 'apartment-images/v4/A10027632.webp', 'afcdff9c7b89834ba737804b2e242c39607d69b582741cd76058d85bcd1fcbeb'),
        ('A10028000', 'apartment-images/v4/A10028000.webp', 'feb6ae519375e9655e12c4f8a6bfb17d1f369517f1e27fecf2f9e45aaa02c912'),
        ('A12071101', 'apartment-images/v4/A12071101.webp', '6f42866eccf23cdcf6a93ebc7804eb9fbb12ac063d14eddc87177c14b4815fd0'),
        ('A12104007', 'apartment-images/v4/A12104007.webp', '3f66c9ff32bcdba045e231531cfdf73990b577f288731f225e3156cb4412b58f'),
        ('A12127003', 'apartment-images/v4/A12127003.webp', 'fb5645ea142cd67b1c4dfe1adc687d071efdc87adb96ddbfc6498c868bea5843'),
        ('A12186901', 'apartment-images/v4/A12186901.webp', 'b195cf48b2e4c3ae8b6b2606e3c45c0a4fa01812085c3e7c22968ce146b2edf1'),
        ('A12187904', 'apartment-images/v4/A12187904.webp', '06244dcda96d64fb4996481a5107aa8c2235643055fd956bc3d8dc782f07d9d5'),
        ('A13003202', 'apartment-images/v4/A13003202.webp', '8fd9a2104218972426a6a8229fc0773b1ad29c6b26eb5da7ca5b4a12fdc3d108'),
        ('A13007002', 'apartment-images/v4/A13007002.webp', '980d93becce259ee8d9ea0d250ed5c92386c4d32453eb477f023e8f6f961f733'),
        ('A13181201', 'apartment-images/v4/A13181201.webp', 'd5d2ee994d56ff81e8fb4973a4c2296fb35860cf19dfcf43f8d8ab8a72e32f5c'),
        ('A13286002', 'apartment-images/v4/A13286002.webp', 'd12451454e6d50f002199197f175c70dbee296c8a29a98a14fd4bd431ef3354a'),
        ('A13407002', 'apartment-images/v4/A13407002.webp', '38aa3a2b491f96ef1c9be125a3deb88eb8df4c8185c0e85a9c866166ba6e09e6'),
        ('A13407202', 'apartment-images/v4/A13407202.webp', '6323acc8dbc5de0161eb2574fcf8069c096753c826df5e11a173538867b4f581'),
        ('A13603401', 'apartment-images/v4/A13603401.webp', '4d3fe93ee77f29db6f670f357d14e1103dec76ca0185bac8be3d7086389ad53d'),
        ('A13610107', 'apartment-images/v4/A13610107.webp', '6fb045558cf881293e8dd68330902abe2e12812990c3dce28e361588ca3ee5ea'),
        ('A13610202', 'apartment-images/v4/A13610202.webp', '35567a67de790d12e82bf0eaf97a3340c8c627620f78078274ba031a2ace8bbf'),
        ('A13611004', 'apartment-images/v4/A13611004.webp', '009329b44ee310c1583cabde4929079f2866fa0baac5baab3680837bc570bda6'),
        ('A13611006', 'apartment-images/v4/A13611006.webp', 'a44cd0dd14dc8495a3b0423b637fc3ba509900d2c52eeb7443acd66b56795f0e'),
        ('A13611007', 'apartment-images/v4/A13611007.webp', '51586866f826a8c101a0f5bc17a08ee9bf872e0899b3cc96d603d577f6e9d939'),
        ('A13611011', 'apartment-images/v4/A13611011.webp', 'e6ed5e3a0a1d716f765be9affb21f3e65bb829afeee598b31cf8b144782e1da8'),
        ('A13611103', 'apartment-images/v4/A13611103.webp', '4d58f4a304e1a8fe30031416cbec6ac1428511aba80c990ebb5b70fe29b85242'),
        ('A13804003', 'apartment-images/v4/A13804003.webp', '33b26c57ce2a8028b5d0177213478233421e0e83ef1595bbfd33b2b944d6f078'),
        ('A13821001', 'apartment-images/v4/A13821001.webp', 'c6bff8544ac0d6773ec156af763eccc17b6b29dece68d7075b0a878bbe2fbaa2'),
        ('A13821004', 'apartment-images/v4/A13821004.webp', 'eec9c0fff39bc32494a5ff3d7ba02a94854a59f314284c9b0f896877a70d5948'),
        ('A13821005', 'apartment-images/v4/A13821005.webp', '5c713cb55a039ce8a6c762b51e14b5ed9ba492f99a512400446480d303c82ee6'),
        ('A13821006', 'apartment-images/v4/A13821006.webp', '141041f3106e213c19f2e5c56e57906b5bc20f220d55000e7d8808cef6ed1436'),
        ('A13821007', 'apartment-images/v4/A13821007.webp', '200cea857198ac7a3000ff66f605a9d205eff020d800450ba54f5f0dffab9bfd'),
        ('A13822003', 'apartment-images/v4/A13822003.webp', 'e7936025f19b07cd500c9643cab3f3c2a8cb668a118786a3edbfa1f54333f49a'),
        ('A13822701', 'apartment-images/v4/A13822701.webp', '10d2ea3323a402aa99c0e76381e8ba1c3cb18e1c075dbf7a91a2dbadd857bbf4'),
        ('A13876108', 'apartment-images/v4/A13876108.webp', 'f5ea6038e7d53228c5c7d1a989aecb813bc12c41796a8985eb6c316cc2b7c254'),
        ('A13880806', 'apartment-images/v4/A13880806.webp', 'e537f801dbcf452ce609e08bfbdf0825267b467275bb88aec554d21b3b53b5e5'),
        ('A13989701', 'apartment-images/v4/A13989701.webp', 'a32bd2c0c0aa7852a6008a6775bddb517ce29a82453b2fe98ced79df9e89446d'),
        ('A15105008', 'apartment-images/v4/A15105008.webp', '4ddcc936b5b87d85f6d515cecbd2ab3f8f806fdd1fa022fd0557758d429171f0'),
        ('A15184101', 'apartment-images/v4/A15184101.webp', 'c93ad47a871d6db89a54001e9f331b2ee496013bfd6bdd7e7ec1ca5c58bc2ac6'),
        ('A15210105', 'apartment-images/v4/A15210105.webp', 'b476e9a521f9ba34cde789674854a080b9f95e910efa2b6a853ceb1b7f1ad892'),
        ('A15672001', 'apartment-images/v4/A15672001.webp', 'c01a16ce49d4124f0070bcaac6b03236b5f16e1b4caade367133bfbab0cc1f38'),
        ('A15704002', 'apartment-images/v4/A15704002.webp', '9fe8999821a53f22bef722426712382fc4578d554b9ec249226e4c4c3832f7fc'),
        ('A15722104', 'apartment-images/v4/A15722104.webp', '85919723e2ce3c399b46f30aed07bbfb2d82ad334d7b2022083a6302eb62e0c9'),
        ('A15807705', 'apartment-images/v4/A15807705.webp', 'a2d16bad57b0114772cc3c92487b00238b485e6c45a038d1545097882f692a16'),
        ('A15881702', 'apartment-images/v4/A15881702.webp', '85c559816aea6f101f34b863487e507e11dba58d5fa535e2ecc5d9e05b8b0bd1'),
        ('A15882104', 'apartment-images/v4/A15882104.webp', '774a2ccdd32389a5d25b207c4f8921d7d96331b1eaeeb4e3b1a4c36088fbc2bc');

UPDATE apartment_image
SET object_key = restyled_v4.object_key,
    sha256 = restyled_v4.sha256
FROM restyled_v4
JOIN apartment ON apartment.complex_code = restyled_v4.complex_code
WHERE apartment_image.apartment_id = apartment.id;

-- 반영 결과 검증.
--
-- 아파트 원본 데이터는 마이그레이션이 아니라 별도 적재로 들어오므로, 빈 DB(신규 환경·CI)
-- 에서는 JOIN 대상이 없어 반영 건수가 0이 된다. 또한 운영 DB에는 로컬에 있는 일부 단지가
-- 없을 수 있다(임대 정리 등). 따라서 절대 개수가 아니라 "대상 단지가 존재하는 만큼
-- 정확히 반영됐는가"라는 상대 정합성으로 검증한다(V33과 동일 방식).
DO $$
DECLARE
    expected_rows BIGINT;
    matched_rows BIGINT;
BEGIN
    SELECT count(*) INTO expected_rows
    FROM apartment a
    JOIN restyled_v4 t ON t.complex_code = a.complex_code
    JOIN apartment_image ai ON ai.apartment_id = a.id;

    SELECT count(*) INTO matched_rows
    FROM apartment a
    JOIN restyled_v4 t ON t.complex_code = a.complex_code
    JOIN apartment_image ai ON ai.apartment_id = a.id
    WHERE ai.object_key = t.object_key
      AND ai.sha256 = t.sha256;

    IF matched_rows <> expected_rows THEN
        RAISE EXCEPTION
            'Expected % restyled v4 apartment images, found %',
            expected_rows, matched_rows;
    END IF;
END
$$;
