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

  # Per-user scoping (spec #15): a sparse GSI keyed on userId so the app queries
  # only the caller's items instead of a full-table scan. Sparse by nature -- the
  # reserved usage#<date> daily-cap counter rows carry no userId attribute and so
  # never appear in this index. projection_type = "ALL" so a per-user query returns
  # full item attributes without a follow-up GetItem.
  attribute {
    name = "userId"
    type = "S"
  }

  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    projection_type = "ALL"
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

  # Per-user scoping (spec #15): a sparse GSI keyed on userId so the app queries
  # only the caller's saved outfits instead of a full-table scan. Sparse by nature
  # -- a legacy pre-ownership outfit (written before spec #15) carries no userId
  # attribute and so never appears in this index. This dedicated outfits table
  # holds no usage#<date> counter rows (those live only in the items table), so
  # there is nothing else to sparse-exclude here. projection_type = "ALL" so a
  # per-user query returns full SavedOutfit attributes without a follow-up GetItem.
  attribute {
    name = "userId"
    type = "S"
  }

  global_secondary_index {
    name            = "userId-index"
    hash_key        = "userId"
    projection_type = "ALL"
  }
}

# User accounts (spec #14). A dedicated table -- email partition key (normalized
# to lowercase by the app), generated userId + bcrypt passwordHash as non-key
# attributes -- deliberately separate from the items/outfits tables, matching the
# single-item, no-GSI data pattern. The running app reaches it through the same
# instance-role grant, already scoped to table/${local.prefix}-* (iam.tf), so
# this adds no IAM diff.
resource "aws_dynamodb_table" "users" {
  name         = "${local.prefix}-users"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "email"

  attribute {
    name = "email"
    type = "S"
  }
}
