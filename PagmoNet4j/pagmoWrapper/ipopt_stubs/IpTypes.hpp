#pragma once
// Minimal stub for IpTypes.hpp — see IpReturnCodes.hpp for rationale.
#include <cstddef>
namespace Ipopt {
  typedef double Number;
  typedef int    Index;
  typedef int    Int;
  typedef std::size_t EJournalCategory;
  typedef std::size_t EJournalLevel;
}
