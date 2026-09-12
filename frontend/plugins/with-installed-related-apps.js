const {
  AndroidConfig,
  withAndroidManifest,
  withStringsXml,
} = require('@expo/config-plugins');

const ASSET_STATEMENTS_RESOURCE = 'asset_statements';
const WEBSITE_ORIGIN = 'https://portfolio.example.com';

const assetStatements = JSON.stringify([
  {
    relation: ['delegate_permission/common.handle_all_urls'],
    target: {
      namespace: 'web',
      site: WEBSITE_ORIGIN,
    },
  },
]).replace(/"/g, '\\"');

function withInstalledRelatedApps(config) {
  config = withAndroidManifest(config, (manifestConfig) => {
    const application = AndroidConfig.Manifest.getMainApplicationOrThrow(
      manifestConfig.modResults,
    );
    AndroidConfig.Manifest.addMetaDataItemToMainApplication(
      application,
      ASSET_STATEMENTS_RESOURCE,
      `@string/${ASSET_STATEMENTS_RESOURCE}`,
      'resource',
    );
    return manifestConfig;
  });

  return withStringsXml(config, (stringsConfig) => {
    AndroidConfig.Strings.setStringItem(
      [
        {
          $: { name: ASSET_STATEMENTS_RESOURCE, translatable: 'false' },
          _: assetStatements,
        },
      ],
      stringsConfig.modResults,
    );
    return stringsConfig;
  });
}

module.exports = withInstalledRelatedApps;
