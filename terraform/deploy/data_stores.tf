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
