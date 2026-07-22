# Runtime data stores: the S3 photos bucket + the DynamoDB items table, matching
# the app's single-item model (see docs/ARCHITECTURE.md "Data Model" —
# itemId partition key, no relational modeling; searchWardrobe scans the table).

resource "aws_s3_bucket" "photos" {
  bucket = "${local.prefix}-photos"
}

resource "aws_s3_bucket_public_access_block" "photos" {
  bucket = aws_s3_bucket.photos.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "photos" {
  bucket = aws_s3_bucket.photos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_dynamodb_table" "items" {
  name         = "${local.prefix}-items"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "itemId"

  attribute {
    name = "itemId"
    type = "S"
  }
}

# Saved outfits (spec #26 Part A). A dedicated table -- outfitId partition key,
# no relational modeling -- deliberately separate from the items table (Q1-A,
# an accepted deviation from the "single-table" note in docs/ARCHITECTURE.md;
# see docs/specs/26-spec-ui-improvements §Technical Considerations). The running
# app reaches it through the same instance-role grant, which is already scoped
# to table/${local.prefix}-* (iam.tf), so this adds no IAM diff.
resource "aws_dynamodb_table" "outfits" {
  name         = "${local.prefix}-outfits"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "outfitId"

  attribute {
    name = "outfitId"
    type = "S"
  }
}
