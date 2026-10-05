# birth-registration-matching-proxy

## Running

```bash
sbt -Dmicroservice.services.birth-registration-matching.username=XXXX -Dmicroservice.services.birth-registration-matching.key=XXXX run
```

## API Documentation

Base endpoint ```/birth-registration-matching-proxy```

### /match/:ref

| PATH                   | Method     | Description                                                               |
|------------------------|------------|---------------------------------------------------------------------------|
| ```/match/reference``` | ```POST``` | Return a childs record for the birth reference number (England and Wales) |

| Parameters | Type     | Size | Description            |
|------------|----------|------|------------------------|
| reference  | `String` | 1-9  | Birth reference number |

### Response

```json
{
  "id": 123456789,
  "date": "2008-08-08",
  "entryNumber": 0,
  "registrar": {
    "signature": "A. Registrar",
    "designation": "Registrar",
    "superintendentSignature": null,
    "superintendentDesignation": null,
    "subdistrict": "Subdistrict town",
    "district": "District city",
    "administrativeArea": "Adminshire"
  },
  "informant1": {
    "forenames": "John Narcissus Ouroboros",
    "surname": "SMITH",
    "address": "888 Dad House, 8 Dad street, Daddington, Dadshire",
    "qualification": "Parent",
    "signature": "J. Smith",
    "signatureIsMark": false
  },
  "informant2": {
    "forenames": "Joan Narcissus Ouroboros",
    "surname": "BLACK",
    "address": null,
    "qualification": "Mother",
    "signature": "J.Smith",
    "signatureIsMark": false
  },
  "child": {
    "originalPrefix": null,
    "prefix": null,
    "forenames": "Joan Narcissus Ouroboros",
    "originalForenames": "John Narcissus Ouroboros",
    "surname": "SMITH",
    "originalSuffix": null,
    "suffix": null,
    "dateOfBirth": "2018-08-08",
    "sex": "Indeterminate",
    "birthplace": "888 Birth House, 8 Birth way, Bournemouth"
  },
  "mother": {
    "prefix": null,
    "forenames": "Joan Narcissus Ouroboros",
    "surname": "BLACK",
    "suffix": null,
    "birthplace": "888 Birth House, 8 Birth way, Bournemouth, B1 7TH",
    "occupation": "Unemployed",
    "aliases": [
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      },
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      },
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      },
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      }
    ],
    "address": null,
    "maidenSurname": "SMITH",
    "marriageSurname": "WHITE"
  },
  "father": {
    "prefix": null,
    "forenames": "John Narcissus Ouroboros",
    "surname": "SMITH",
    "suffix": null,
    "birthplace": "888 Birth House, 8 Birth way, Bournemouth, B1 7TH",
    "occupation": "Unemployed",
    "aliases": [
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      },
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      },
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      },
      {
        "prefix": null,
        "forenames": null,
        "surname": null,
        "suffix": null
      }
    ],
    "deceased": false
  },
  "dateOfDeclaration": null,
  "dateOfStatutoryDeclarationOfParentage": null,
  "statutoryDeclarationOfParentage": null,
  "dateOfNameUpdate": null,
  "status": {
    "blocked": false,
    "cancelled": false,
    "correction": "None",
    "marginalNote": "None",
    "nameUpdate": "None",
    "onAuthorityOfRegistrarGeneral": false,
    "potentiallyFictitious": false,
    "praOrCourtOrder": "None",
    "reregistration": "None"
  },
  "nextRegistration": null,
  "previousRegistration": null
}
```

### /match

| PATH                 | Method     | Description                                                                          |
|----------------------|------------|--------------------------------------------------------------------------------------|
| ```/match/details``` | ```POST``` | Return child(ren)s record(s) by searching with forenames, lastname and date of birth | 

| Parameters  | Type                | Size  | Description                      |
|-------------|---------------------|-------|----------------------------------|
| forenames   | `String`            | 1-250 | Child's first name               |
| lastname    | `String`            | 1-250 | Child's last name                |
| dateofbirth | `Date (yyyy-MM-dd)` | 10    | Child's date of birth YYYY-MM-DD |

### Response

```json
[
  {
    "id": 123456789,
    "date": "2008-08-08",
    "entryNumber": 0,
    "registrar": {
      "signature": "A. Registrar",
      "designation": "Registrar",
      "superintendentSignature": null,
      "superintendentDesignation": null,
      "subdistrict": "Subdistrict town",
      "district": "District city",
      "administrativeArea": "Adminshire"
    },
    "informant1": {
      "forenames": "John Narcissus Ouroboros",
      "surname": "SMITH",
      "address": "888 Dad House, 8 Dad street, Daddington, Dadshire",
      "qualification": "Parent",
      "signature": "J. Smith",
      "signatureIsMark": false
    },
    "informant2": {
      "forenames": "Joan Narcissus Ouroboros",
      "surname": "BLACK",
      "address": null,
      "qualification": "Mother",
      "signature": "J.Smith",
      "signatureIsMark": false
    },
    "child": {
      "originalPrefix": null,
      "prefix": null,
      "forenames": "Adàm TËST",
      "originalForenames": "John Narcissus Ouroboros",
      "surname": "SMÏTH",
      "originalSuffix": null,
      "suffix": null,
      "dateOfBirth": "2006-11-12",
      "sex": "Indeterminate",
      "birthplace": "888 Birth House, 8 Birth way, Bournemouth"
    },
    "mother": {
      "prefix": null,
      "forenames": "Joan Narcissus Ouroboros",
      "surname": "BLACK",
      "suffix": null,
      "birthplace": "888 Birth House, 8 Birth way, Bournemouth, B1 7TH",
      "occupation": "Unemployed",
      "aliases": [
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        }
      ],
      "address": null,
      "maidenSurname": "SMITH",
      "marriageSurname": "WHITE"
    },
    "father": {
      "prefix": null,
      "forenames": "John Narcissus Ouroboros",
      "surname": "SMITH",
      "suffix": null,
      "birthplace": "888 Birth House, 8 Birth way, Bournemouth, B1 7TH",
      "occupation": "Unemployed",
      "aliases": [
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        }
      ],
      "deceased": false
    },
    "dateOfDeclaration": null,
    "dateOfStatutoryDeclarationOfParentage": null,
    "statutoryDeclarationOfParentage": null,
    "dateOfNameUpdate": null,
    "status": {
      "blocked": false,
      "cancelled": false,
      "correction": "None",
      "marginalNote": "None",
      "nameUpdate": "None",
      "onAuthorityOfRegistrarGeneral": false,
      "potentiallyFictitious": false,
      "praOrCourtOrder": "None",
      "reregistration": "None"
    },
    "nextRegistration": null,
    "previousRegistration": null
  },
  {
    "id": 123456789,
    "date": "2008-08-08",
    "entryNumber": 0,
    "registrar": {
      "signature": "A. Registrar",
      "designation": "Registrar",
      "superintendentSignature": null,
      "superintendentDesignation": null,
      "subdistrict": "Subdistrict town",
      "district": "District city",
      "administrativeArea": "Adminshire"
    },
    "informant1": {
      "forenames": "John Narcissus Ouroboros",
      "surname": "SMITH",
      "address": "888 Dad House, 8 Dad street, Daddington, Dadshire",
      "qualification": "Parent",
      "signature": "J. Smith",
      "signatureIsMark": false
    },
    "informant2": {
      "forenames": "Joan Narcissus Ouroboros",
      "surname": "BLACK",
      "address": null,
      "qualification": "Mother",
      "signature": "J.Smith",
      "signatureIsMark": false
    },
    "child": {
      "originalPrefix": null,
      "prefix": null,
      "forenames": "Adàm TËST",
      "originalForenames": "John Narcissus Ouroboros",
      "surname": "SMÏTH",
      "originalSuffix": null,
      "suffix": null,
      "dateOfBirth": "2006-11-12",
      "sex": "Indeterminate",
      "birthplace": "888 Birth House, 8 Birth way, Bournemouth"
    },
    "mother": {
      "prefix": null,
      "forenames": "Joan Narcissus Ouroboros",
      "surname": "BLACK",
      "suffix": null,
      "birthplace": "888 Birth House, 8 Birth way, Bournemouth, B1 7TH",
      "occupation": "Unemployed",
      "aliases": [
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        }
      ],
      "address": null,
      "maidenSurname": "SMITH",
      "marriageSurname": "WHITE"
    },
    "father": {
      "prefix": null,
      "forenames": "John Narcissus Ouroboros",
      "surname": "SMITH",
      "suffix": null,
      "birthplace": "888 Birth House, 8 Birth way, Bournemouth, B1 7TH",
      "occupation": "Unemployed",
      "aliases": [
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        },
        {
          "prefix": null,
          "forenames": null,
          "surname": null,
          "suffix": null
        }
      ],
      "deceased": false
    },
    "dateOfDeclaration": null,
    "dateOfStatutoryDeclarationOfParentage": null,
    "statutoryDeclarationOfParentage": null,
    "dateOfNameUpdate": null,
    "status": {
      "blocked": false,
      "cancelled": false,
      "correction": "None",
      "marginalNote": "None",
      "nameUpdate": "None",
      "onAuthorityOfRegistrarGeneral": false,
      "potentiallyFictitious": false,
      "praOrCourtOrder": "None",
      "reregistration": "None"
    },
    "nextRegistration": null,
    "previousRegistration": null
  }
]
```

## License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
