// pageToc.js
//
// Ensure all headings are added to the page table of contents. Adapted from 
// http://www.bright-green.com/blog/2004_06_02/javascript_table_of_contents.html

function createTOC()
{
    // find the nodes to be added to the Page TOC

  var tocTargets = new Array()
  nodes = document.getElementById('body').childNodes
  for (var i = 0; i < nodes.length; i++)
  {
    nn = nodes[i].nodeName
    if (nn == "H1" || nn == "H2" || nn == "H3" ||
        nn == "H4" || nn == "H5" || nn == "H6")
    {
      tocTargets.push(nodes[i])
    }
  }

  tocDiv = document.getElementById('toc')

  // Remove toc if none or one heading
  if (tocTargets.length <= 1)
  {
    tocDiv.parentNode.removeChild(tocDiv)
    return;
  }

  // Add the toc contents
  tocDiv = document.getElementById('toc')
  //tocDiv.innerHTML= "<h2>Page Contents </h2>"
  tocList = document.createElement('ul')
  tocList.className = 'toc'
  tocDiv.appendChild(tocList)
  // Insert elements into our table of contents
  for (var i = 0; i < tocTargets.length; i++)
  {
    tocTarget = tocTargets[i]
    if (tocTarget.id == '')
    {
      tocTarget.id = 'toc' + i
    }

    newItem = document.createElement('li')
    newItem.className = 'toc' + tocTarget.nodeName
    newLink = document.createElement('a')
    newLink.href = '#' + tocTarget.id
    newLink.innerHTML = tocTarget.innerHTML
    newItem.appendChild(newLink)
    tocList.appendChild(newItem)
  }  
}
