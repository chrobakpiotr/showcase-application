import packageInfo from '../../package.json';

export const environmentBase = {
  production: false,
  appVersion: packageInfo.version,
  apiPrefix: '/home/api',
  authTokenUrl:
    'http://localhost:8081/realms/ecommerce/protocol/openid-connect/token',
  clientId: 'ecommerce-app',
};
