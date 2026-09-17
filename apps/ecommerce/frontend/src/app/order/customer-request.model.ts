// Field constraints mirror the backend domain objects (Contact/Address in the domain module):
// fullName <= 80, email <= 255 + RFC email format, phone <= 25 matching pattern ^$|[- +()0-9]+,
// street required <= 35, postalCode <= 35, city required <= 300, countryCode required (ISO 3166-1 alpha-2).
export interface CustomerRequestModel {
  fullName: string;
  email: string;
  phone: string;
  street: string;
  postalCode: string;
  city: string;
  countryCode: string;
}
